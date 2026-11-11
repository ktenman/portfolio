package ee.tenman.portfolio.service.poff

import ee.tenman.portfolio.domain.PoffFilm
import ee.tenman.portfolio.imdb.ImdbSuggestion
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.text.Normalizer
import java.util.zip.GZIPInputStream
import kotlin.math.abs

data class ImdbMatch(
  val id: String,
  val score: Int,
)

data class ImdbVotes(
  val rating: BigDecimal,
  val votes: Int,
)

private const val THRESHOLD = 4

private const val SHORT_MINUTES = 45

private val FILM_KINDS = setOf("movie", "short", "tvMovie", "tvShort", "tvSpecial", "video")

private val SHORT_KINDS = setOf("short", "tvShort")

private val FEATURE_KINDS = setOf("movie", "tvMovie")

private val MARKS = Regex("""\p{M}+""")

private val SEPARATORS = Regex("""[^\p{L}\p{N}]+""")

fun normalise(text: String): String =
  Normalizer
    .normalize(text, Normalizer.Form.NFD)
    .replace(MARKS, "")
    .lowercase()
    .replace(SEPARATORS, " ")
    .trim()

fun titles(film: PoffFilm): List<String> =
  listOfNotNull(film.title, film.englishTitle, film.originalTitle).map(::normalise).filter { it.isNotEmpty() }.distinct()

fun score(
  film: PoffFilm,
  candidate: ImdbSuggestion,
): Int? {
  if (!candidate.id.startsWith("tt") || candidate.qid !in FILM_KINDS) return null
  val gap = film.year?.let { year -> candidate.year?.let { abs(it - year) } } ?: return null
  if (gap > 1) return null
  val year = if (gap == 0) 3 else 1
  val title = if (candidate.title?.let(::normalise) in titles(film)) 2 else 0
  val actors = sharedActors(film, candidate)
  if (title == 0 && actors == 0) return null
  return year + title + kindPoints(film, candidate) + 3 * actors
}

fun bestMatch(
  film: PoffFilm,
  candidates: List<ImdbSuggestion>,
): ImdbMatch? =
  candidates
    .mapNotNull { candidate -> score(film, candidate)?.let { ImdbMatch(candidate.id, it) } }
    .filter { it.score >= THRESHOLD }
    .maxByOrNull { it.score }

fun readOverrides(text: String): Map<Long, String?> =
  text
    .lines()
    .drop(1)
    .filter { it.isNotBlank() }
    .associate { line -> line.split("\t").let { it[0].trim().toLong() to it[1].trim().takeUnless { id -> id == "-" } } }

fun readRatings(
  gzip: ByteArray,
  ids: Set<String>,
): Map<String, ImdbVotes> =
  GZIPInputStream(ByteArrayInputStream(gzip)).bufferedReader().useLines { lines ->
    lines
      .drop(1)
      .filter { it.substringBefore('\t') in ids }
      .map { it.split("\t") }
      .filter { it.size == 3 }
      .associate { it[0] to ImdbVotes(BigDecimal(it[1]), it[2].toInt()) }
  }

private fun kindPoints(
  film: PoffFilm,
  candidate: ImdbSuggestion,
): Int {
  val minutes = film.minutes ?: return 0
  return when {
    minutes < SHORT_MINUTES && candidate.qid in SHORT_KINDS -> 2
    minutes >= SHORT_MINUTES && candidate.qid in FEATURE_KINDS -> 1
    else -> 0
  }
}

private fun sharedActors(
  film: PoffFilm,
  candidate: ImdbSuggestion,
): Int {
  val cast =
    film.credits
    .filter { it.role == "Cast" }
    .flatMap { it.names }
    .map(::words)
  val stars =
    candidate.stars
    .orEmpty()
    .split(",")
    .map(::words)
    .filter { it.isNotEmpty() }
  return stars.count { star -> cast.any { samePerson(star, it) } }
}

private fun words(name: String): Set<String> = normalise(name).split(" ").filter { it.isNotEmpty() }.toSet()

private fun samePerson(
  first: Set<String>,
  second: Set<String>,
): Boolean {
  if (first.isEmpty() || second.isEmpty()) return false
  return if (first.size <= second.size) second.containsAll(first) else first.containsAll(second)
}
