package ee.tenman.portfolio.service.poff

import ee.tenman.portfolio.domain.PoffCatalogue
import ee.tenman.portfolio.poff.PoffClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class PoffImportService(
  private val poffClient: PoffClient,
  @Value("\${poff.pause}") private val pause: Duration,
) {
  fun listing(): List<PoffListing> = parseListing(poffClient.films())

  fun fetch(listing: List<PoffListing>): PoffCatalogue =
    PoffCatalogue(
      listing.map { film ->
        Thread.sleep(pause)
        runCatching { parseFilm(film, poffClient.film(film.slug)) }
          .getOrElse { throw IllegalStateException("Film page ${film.slug} cannot be imported: ${it.message}", it) }
      },
    )
}
