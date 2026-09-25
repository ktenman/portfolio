package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.LightyearScrapingProperties
import ee.tenman.portfolio.exception.PriceRefreshException
import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.model.CollectionRunResult
import ee.tenman.portfolio.model.ProcessResult
import ee.tenman.portfolio.scheduler.MarketPhaseDetectionService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.pricing.LightyearPriceUpdateService
import ee.tenman.portfolio.service.pricing.PriceUpdateProcessor
import ee.tenman.portfolio.testing.fixture.monitorForTests
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LightyearPriceRetrievalJobTest {
  @Test
  fun `should fail when none of the configured instruments return prices`() {
    val job = job(emptyMap())

    expect { job.execute() }.toThrow<PriceRefreshException>()
  }

  @Test
  fun `should preserve available prices before reporting a partial collection failure`() {
    val persisted = mutableListOf<String>()
    val runs = mutableListOf<CollectionRunResult>()
    val job = job(mapOf("VGLA:GER:EUR" to BigDecimal("4.38")), persisted, runs)

    expect { job.execute() }.toThrow<PriceRefreshException>()
    expect(persisted).toContainExactly("VGLA:GER:EUR")
    expect(runs.single().persisted).toContainExactly("VGLA:GER:EUR")
    expect(runs.single().failed).toContainExactly("WEBN:GER:EUR")
  }

  @Test
  fun `should persist every instrument after a complete collection`() {
    val persisted = mutableListOf<String>()
    val prices = mapOf("VGLA:GER:EUR" to BigDecimal("4.38"), "WEBN:GER:EUR" to BigDecimal("13.12"))

    job(prices, persisted).execute()

    expect(persisted).toContainExactly("VGLA:GER:EUR", "WEBN:GER:EUR")
  }

  private fun job(
    prices: Map<String, BigDecimal>,
    persisted: MutableList<String> = mutableListOf(),
    runs: MutableList<CollectionRunResult> = mutableListOf(),
  ): LightyearPriceRetrievalJob {
    val clock = Clock.fixed(Instant.parse("2026-09-25T13:30:00Z"), ZoneOffset.UTC)
    val service = mockk<LightyearPriceService>()
    val updates = mockk<LightyearPriceUpdateService>()
    val market = mockk<MarketPhaseDetectionService>()
    every { service.fetchCurrentPrices(any()) } returns prices
    every { market.isWeekendPhase() } returns false
    every { updates.processSymbol(any(), any(), any(), any()) } answers {
      persisted.add(firstArg())
      ProcessResult.SUCCESS_WITH_DAILY_PRICE
    }
    val processor = PriceUpdateProcessor(market, clock, mockk(), mockk(), mockk())
    val properties =
      LightyearScrapingProperties(
        listOf(
          LightyearScrapingProperties.EtfConfig("VGLA:GER:EUR", "vgla-uuid"),
          LightyearScrapingProperties.EtfConfig("WEBN:GER:EUR", "webn-uuid"),
        ),
      )
    return LightyearPriceRetrievalJob(mockk<JobExecutionService>(), service, updates, processor, clock, properties, monitorForTests(runs))
  }
}
