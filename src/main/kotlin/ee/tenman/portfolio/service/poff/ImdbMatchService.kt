package ee.tenman.portfolio.service.poff

import ee.tenman.portfolio.domain.PoffFilm
import ee.tenman.portfolio.domain.PoffImdb
import ee.tenman.portfolio.imdb.ImdbSuggestion
import ee.tenman.portfolio.imdb.ImdbSuggestionClient
import ee.tenman.portfolio.repository.PoffImdbRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

private val RETRY = Duration.ofDays(1)
private const val FAILURES = 3

@Service
class ImdbMatchService(
  private val imdbSuggestionClient: ImdbSuggestionClient,
  private val poffImdbRepository: PoffImdbRepository,
  private val clock: Clock,
  @Value("classpath:poff/imdb-overrides.tsv") overrides: Resource,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val overrides: Map<Long, String?> = readOverrides(overrides.getContentAsString(Charsets.UTF_8))

  fun match(films: List<PoffFilm>): Int {
    val rows = poffImdbRepository.findAll().associateBy { it.filmId }
    val unique = films.distinctBy { it.id }
    val overridden = unique.filter { it.id in overrides }.count { save(it, rows[it.id], overrides[it.id], null) }
    var failures = 0
    val searched =
      unique
        .asSequence()
        .filter { searchable(it, rows[it.id]) }
        .takeWhile { failures < FAILURES }
        .map { search(it, rows[it.id]) }
        .onEach { failures = if (it == null) failures + 1 else 0 }
        .count { it == true }
    return overridden + searched
  }

  private fun searchable(
    film: PoffFilm,
    row: PoffImdb?,
  ): Boolean {
    if (film.id in overrides || film.year == null) return false
    if (row == null) return true
    if (row.imdbId != null && row.score != null) return false
    return !row.triedAt.plus(RETRY).isAfter(clock.instant())
  }

  private fun search(
    film: PoffFilm,
    row: PoffImdb?,
  ): Boolean? {
    val candidates =
      runCatching { suggestions(film) }.getOrElse {
        log.warn("IMDb search failed for PÖFF film ${film.id} ${film.englishTitle}", it)
        return null
      }
    val best = bestMatch(film, candidates)
    return save(film, row, best?.id, best?.score)
  }

  private fun suggestions(film: PoffFilm): List<ImdbSuggestion> =
    titles(film).flatMap { imdbSuggestionClient.suggest(it).d }.distinctBy { it.id }

  private fun save(
    film: PoffFilm,
    row: PoffImdb?,
    imdbId: String?,
    score: Int?,
  ): Boolean {
    if (row != null && row.imdbId == imdbId && row.score == score && imdbId != null) return false
    val entry = row ?: PoffImdb(film.id, null, null, null, null, clock.instant())
    entry.imdbId = imdbId
    entry.score = score
    entry.rating = null
    entry.votes = null
    entry.triedAt = clock.instant()
    poffImdbRepository.save(entry)
    return imdbId != null
  }
}
