package ee.tenman.portfolio.job

import ee.tenman.portfolio.configuration.LightyearScrapingProperties
import ee.tenman.portfolio.configuration.LightyearScrapingProperties.EtfConfig
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.service.etf.EtfBreakdownService
import ee.tenman.portfolio.service.etf.EtfHoldingService
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LightyearDataFetchJobTest {
  private val jobTransactionService: JobTransactionService = mockk(relaxed = true)
  private val properties: LightyearScrapingProperties = mockk()
  private val lightyearPriceService: LightyearPriceService = mockk()
  private val etfHoldingService: EtfHoldingService = mockk(relaxed = true)
  private val etfBreakdownService: EtfBreakdownService = mockk(relaxed = true)
  private val clock: Clock = Clock.fixed(Instant.parse("2026-09-22T09:00:00Z"), ZoneOffset.UTC)

  private val job =
    LightyearDataFetchJob(
      jobTransactionService = jobTransactionService,
      properties = properties,
      lightyearPriceService = lightyearPriceService,
      etfHoldingService = etfHoldingService,
      etfBreakdownService = etfBreakdownService,
      clock = clock,
    )

  @BeforeEach
  fun setup() {
    every { properties.etfs } returns
      listOf(
        EtfConfig("VGLA:GER:EUR", "1f1a07ba-aa44-690a-bc0c-05e2425f8ab8"),
        EtfConfig("VXUS:GER:EUR", "1f1a5e78-d59c-625c-9dd3-ab6a18f976ac"),
        EtfConfig("VWCE:GER:EUR", "1eda0a07-10b3-63e0-b568-6deedaa217e7"),
      )
    every { etfHoldingService.hasHoldingsForDate(any(), any()) } returns false
    every { lightyearPriceService.fetchHoldingsAsDto(any()) } returns
      listOf(HoldingData(name = "Apple Inc", ticker = "AAPL", sector = null, weight = BigDecimal("100"), rank = 1))
  }

  @Test
  fun `cannot fetch Lightyear holdings for funds imported from Vanguard`() {
    job.execute()

    verify(exactly = 0) { lightyearPriceService.fetchHoldingsAsDto("VGLA:GER:EUR") }
  }

  @Test
  fun `cannot save Lightyear holdings for funds imported from Vanguard`() {
    job.execute()

    verify(exactly = 0) { etfHoldingService.saveHoldings("VXUS:GER:EUR", any(), any()) }
  }

  @Test
  fun `should fetch Lightyear holdings for funds that Vanguard does not cover`() {
    job.execute()

    verify(exactly = 1) { lightyearPriceService.fetchHoldingsAsDto("VWCE:GER:EUR") }
  }
}
