package ee.tenman.portfolio.service.poff

import ee.tenman.portfolio.configuration.PoffProperties
import ee.tenman.portfolio.domain.PoffCatalogue
import ee.tenman.portfolio.poff.PoffClient
import org.springframework.stereotype.Service

@Service
class PoffImportService(
  private val poffClient: PoffClient,
  private val poffProperties: PoffProperties,
) {
  fun listing(): List<PoffListing> = parseListing(poffClient.films())

  fun fetch(listing: List<PoffListing>): PoffCatalogue =
    PoffCatalogue(
      listing.map { film ->
        Thread.sleep(poffProperties.pause)
        runCatching { parseFilm(film, poffClient.film(film.slug)) }
          .getOrElse { throw IllegalStateException("Film page ${film.slug} cannot be imported: ${it.message}", it) }
      },
    )
}
