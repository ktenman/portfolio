package ee.tenman.portfolio.service.monitoring

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.LightyearScrapingProperties
import ee.tenman.portfolio.configuration.Trading212ScrapingProperties
import ee.tenman.portfolio.configuration.Trading212SymbolEntry
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.InstrumentRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class CollectionInventoryServiceTest {
  @Test
  fun `should initialize every operation when no database instruments exist`() {
    val repository = mockk<InstrumentRepository> { every { findAll() } returns emptyList() }
    val service = CollectionInventoryService(repository, LightyearScrapingProperties(), Trading212ScrapingProperties())
    expect(service.configured().keys).toEqual(CollectionKey.entries.toSet())
  }

  @Test
  fun `should monitor only Trading212 holdings configured for an active provider instrument`() {
    val repository =
      mockk<InstrumentRepository> {
      every { findAll() } returns listOf(instrument("BNKE:PAR:EUR", ProviderName.TRADING212), instrument("OTHER", ProviderName.FT))
    }
    val properties =
      Trading212ScrapingProperties().apply {
      symbols = mutableListOf(Trading212SymbolEntry("BNKE:PAR:EUR", "BNKEp_EQ"), Trading212SymbolEntry("OTHER", "OTHER_EQ"))
    }
    val service = CollectionInventoryService(repository, LightyearScrapingProperties(), properties)
    expect(service.configured()[CollectionKey.TRADING212_HOLDINGS]).toEqual(setOf("BNKE:PAR:EUR"))
  }

  @Test
  fun `should distinguish Lightyear price coverage from supported equity holdings`() {
    val repository = mockk<InstrumentRepository> { every { findAll() } returns emptyList() }
    val lightyear =
      LightyearScrapingProperties(
        listOf("WEBN:GER:EUR", "VGLA:GER:EUR", "GOOGL:NSQ:USD", "WBIT:GER:EUR")
      .map { LightyearScrapingProperties.EtfConfig(it, "uuid") },
          )
    val service = CollectionInventoryService(repository, lightyear, Trading212ScrapingProperties())
    expect(service.configured()[CollectionKey.LIGHTYEAR_HOLDINGS]).toEqual(setOf("WEBN:GER:EUR"))
  }

  private fun instrument(
    symbol: String,
    provider: ProviderName,
  ): Instrument = Instrument(symbol, symbol, "ETF", "EUR", providerName = provider)
}
