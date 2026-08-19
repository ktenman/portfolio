package ee.tenman.portfolio.job

import ee.tenman.portfolio.blackrock.CsusHoldingsService
import ee.tenman.portfolio.configuration.HoldingReconciliationProperties
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.model.ReconciliationResult
import ee.tenman.portfolio.service.etf.EtfBreakdownService
import ee.tenman.portfolio.service.etf.EtfHoldingService
import ee.tenman.portfolio.service.etf.HoldingReconciliationService
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class HoldingReconciliationJobTest {
  private val holdingReconciliationService: HoldingReconciliationService = mockk(relaxed = true)

  private fun job(
    enabled: Boolean,
    dryRun: Boolean,
  ): HoldingReconciliationJob =
    HoldingReconciliationJob(
      holdingReconciliationService,
      HoldingReconciliationProperties(enabled = enabled, dryRun = dryRun),
    )

  @Test
  fun `cannot reconcile when reconciliation is disabled`() {
    job(enabled = false, dryRun = false).runJob()

    verify(exactly = 0) { holdingReconciliationService.reconcile(any()) }
  }

  @Test
  fun `should reconcile in dry run mode when enabled and dry run is on`() {
    every { holdingReconciliationService.reconcile(true) } returns ReconciliationResult(mergedGroups = 0, mergedDuplicates = 0)

    job(enabled = true, dryRun = true).runJob()

    verify(exactly = 1) { holdingReconciliationService.reconcile(true) }
  }

  @Test
  fun `should reconcile with mutations when enabled and dry run is off`() {
    every { holdingReconciliationService.reconcile(false) } returns ReconciliationResult(mergedGroups = 1, mergedDuplicates = 2)

    job(enabled = true, dryRun = false).runJob()

    verify(exactly = 1) { holdingReconciliationService.reconcile(false) }
  }
}

class CsusHoldingsRetrievalJobTest {
  private val jobTransactionService = mockk<JobTransactionService>(relaxed = true)
  private val csusHoldingsService = mockk<CsusHoldingsService>()
  private val etfHoldingService = mockk<EtfHoldingService>()
  private val etfBreakdownService = mockk<EtfBreakdownService>(relaxed = true)
  private val clock = Clock.fixed(LocalDate.of(2026, 6, 13).atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"))
  private val job =
    CsusHoldingsRetrievalJob(jobTransactionService, csusHoldingsService, etfHoldingService, etfBreakdownService, clock)

  private val today = LocalDate.of(2026, 6, 13)
  private val symbol = "GB00B0ZDNB53:GBP"

  @Test
  fun `should save fetched holdings against aviva symbol when none exist for today`() {
    val holdings =
      listOf(
        HoldingData(name = "NVIDIA CORP", ticker = "NVDA", sector = "Information Technology", weight = BigDecimal("7.42"), rank = 1),
      )
    every { etfHoldingService.hasHoldingsForDate(symbol, today) } returns false
    every { csusHoldingsService.fetchHoldings() } returns holdings
    every { etfHoldingService.saveHoldings(symbol, today, holdings) } just Runs

    job.execute()

    verify(exactly = 1) { etfHoldingService.saveHoldings(symbol, today, holdings) }
  }

  @Test
  fun `should skip fetch when holdings already exist for today`() {
    every { etfHoldingService.hasHoldingsForDate(symbol, today) } returns true

    job.execute()

    verify(exactly = 0) { csusHoldingsService.fetchHoldings() }
    verify(exactly = 0) { etfHoldingService.saveHoldings(any(), any(), any()) }
  }
}
