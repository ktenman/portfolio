package ee.tenman.portfolio.lightyear

import ee.tenman.portfolio.configuration.LightyearScrapingProperties
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.currency.CurrencyConversionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

abstract class LightyearPriceServiceTestBase {
  protected val lightyearPriceClient = mockk<LightyearPriceClient>()
  protected val properties = mockk<LightyearScrapingProperties>()
  protected val instrumentRepository = mockk<InstrumentRepository>()
  protected val instrumentService = mockk<InstrumentService>()
  protected val uuidCacheService = mockk<LightyearUuidCacheService>()
  protected val currencyConversionService = mockk<CurrencyConversionService>()
  protected val clock: Clock = Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC)
  protected val service =
    LightyearPriceService(
      lightyearPriceClient,
      properties,
      instrumentRepository,
      instrumentService,
      uuidCacheService,
      currencyConversionService,
      clock,
    )

  @BeforeEach
  fun setUp() {
    every { uuidCacheService.getCachedUuid(any()) } returns null
    every { uuidCacheService.cacheUuid(any(), any()) } answers { secondArg() }
    setupExchangeMapping()
  }

  private fun setupExchangeMapping() {
    every { properties.convertExchangeToLightyear("GER") } returns "XETRA"
    every { properties.convertExchangeToLightyear("AEX") } returns "AMS"
    every { properties.convertExchangeToLightyear("LON") } returns "LSE"
    every { properties.convertExchangeToLightyear(match { it !in listOf("GER", "AEX", "LON") }) } answers { firstArg() }
  }
}
