package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.model.CollectionRunResult
import ee.tenman.portfolio.service.etf.EtfHoldingService
import ee.tenman.portfolio.service.infrastructure.CacheInvalidationService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.testing.fixture.monitorForTests
import ee.tenman.portfolio.tuleva.TulevaHoldingsService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class TulevaHoldingsRetrievalJobTest {
  private val service = mockk<TulevaHoldingsService>(relaxed = true) { every { lookThrough() } returns HOLDINGS }
  private val etfHoldingService = mockk<EtfHoldingService>(relaxed = true)
  private val collections = mutableListOf<CollectionRunResult>()
  private val job =
    TulevaHoldingsRetrievalJob(
      service,
      etfHoldingService,
      mockk<CacheInvalidationService>(relaxed = true),
      mockk<JobExecutionService>(relaxed = true),
      Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant(), ZoneId.of("Europe/Tallinn")),
      monitorForTests(collections),
    )

  @Test
  fun `should mark the fund persisted when no new report is published`() {
    job.execute()
    expect(collections.single().persisted).toContainExactly(TulevaHoldingsService.SYMBOL)
  }

  @Test
  fun `should refresh holdings from the latest imported report when a new report fails to import`() {
    every { service.importReports() } throws IllegalArgumentException("Tuleva report has no report date")
    runCatching { job.execute() }
    verify { etfHoldingService.saveHoldings(TulevaHoldingsService.SYMBOL, TODAY, HOLDINGS) }
  }

  @Test
  fun `should mark the fund failed when a new report fails to import`() {
    every { service.importReports() } throws IllegalArgumentException("Tuleva report has no report date")
    runCatching { job.execute() }
    expect(collections.single().failed).toContainExactly(TulevaHoldingsService.SYMBOL)
  }

  @Test
  fun `should report the import failure when no report was ever imported`() {
    every { service.importReports() } throws IllegalArgumentException("Tuleva report has no report date")
    every { service.lookThrough() } throws IllegalArgumentException("No Tuleva allocation report imported")
    expect { job.execute() }.toThrow<IllegalArgumentException>().messageToContain("no report date")
  }

  @Test
  fun `should keep the look-through failure alongside the import failure`() {
    every { service.importReports() } throws IllegalArgumentException("Tuleva report has no report date")
    every { service.lookThrough() } throws IllegalStateException("BlackRock is unavailable")
    val failure = runCatching { job.execute() }.exceptionOrNull()
    expect(failure?.suppressed.orEmpty().map { it.message }).toContainExactly("BlackRock is unavailable")
  }

  companion object {
    private val TODAY = LocalDate.of(2026, 9, 29)
    private val HOLDINGS = listOf(HoldingData(name = "Nvidia Corp", ticker = "NVDA", sector = null, weight = BigDecimal("5.12"), rank = 1))
  }
}
