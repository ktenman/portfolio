package ee.tenman.portfolio.domain

import java.math.BigDecimal
import java.time.Instant

data class PoffCatalogue(
  val films: List<PoffFilm>,
)

data class PoffCatalogueResponse(
  val syncedAt: Instant,
  val films: List<PoffFilm>,
)

data class PoffFilm(
  val id: Long,
  val slug: String,
  val title: String,
  val englishTitle: String,
  val originalTitle: String?,
  val premiere: String?,
  val directors: List<String>,
  val countries: List<String>,
  val year: Int?,
  val minutes: Int?,
  val programmes: List<String>,
  val genres: List<String>,
  val subjects: List<String>,
  val moods: List<String>,
  val synopsis: List<String>,
  val languages: List<String>,
  val subtitles: List<String>,
  val credits: List<PoffCredit>,
  val stills: List<String>,
  val trailer: String?,
  val screenings: List<PoffScreening>,
  val imdb: ImdbRating? = null,
)

data class PoffCredit(
  val role: String,
  val names: List<String>,
)

data class PoffScreening(
  val id: Long,
  val start: Instant,
  val end: Instant,
  val cinema: String,
  val city: String,
  val hall: String,
  val code: String?,
  val qa: Boolean,
  val qaMinutes: Int?,
  val guests: String?,
  val ticket: Long?,
  val soldOut: Boolean,
)

data class ImdbRating(
  val id: String,
  val rating: BigDecimal?,
  val votes: Int?,
)
