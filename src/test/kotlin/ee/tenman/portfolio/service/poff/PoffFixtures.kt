package ee.tenman.portfolio.service.poff

import ee.tenman.portfolio.domain.PoffCredit
import ee.tenman.portfolio.domain.PoffFilm
import ee.tenman.portfolio.domain.PoffScreening
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.zip.GZIPOutputStream

fun tallinnClock(at: String): Clock = Clock.fixed(Instant.parse(at), ZoneId.of("Europe/Tallinn"))

fun ratingsFile(vararg lines: String): ByteArray {
  val bytes = ByteArrayOutputStream()
  GZIPOutputStream(bytes).use { it.write((listOf("tconst\taverageRating\tnumVotes") + lines).joinToString("\n").toByteArray()) }
  return bytes.toByteArray()
}

fun poffScreening(
  id: Long,
  start: String,
  city: String = "Tallinn",
) = PoffScreening(
  id = id,
  start = Instant.parse(start),
  end = Instant.parse(start).plus(Duration.ofMinutes(100)),
  cinema = "Apollo Kino Plaza",
  city = city,
  hall = "1st Lux Hall",
  code = null,
  qa = false,
  qaMinutes = null,
  guests = null,
  ticket = id + 500000,
  soldOut = false,
)

fun poffFilm(
  id: Long,
  title: String = "Kõlakamber",
  englishTitle: String = "The Echo Chamber",
  year: Int? = 2026,
  minutes: Int? = 98,
  cast: List<String> = emptyList(),
  screenings: List<PoffScreening> = listOf(poffScreening(id, "2026-11-10T16:00:00Z")),
) = PoffFilm(
  id = id,
  slug = "film-$id",
  title = title,
  englishTitle = englishTitle,
  originalTitle = null,
  premiere = null,
  directors = emptyList(),
  countries = emptyList(),
  year = year,
  minutes = minutes,
  programmes = emptyList(),
  genres = listOf("Drama"),
  subjects = emptyList(),
  moods = emptyList(),
  synopsis = emptyList(),
  languages = emptyList(),
  subtitles = emptyList(),
  credits = listOf(PoffCredit("Cast", cast)),
  stills = emptyList(),
  trailer = null,
  screenings = screenings,
)
