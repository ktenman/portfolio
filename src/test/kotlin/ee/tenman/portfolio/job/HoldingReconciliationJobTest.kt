package ee.tenman.portfolio.job

import ee.tenman.portfolio.configuration.HoldingReconciliationProperties
import ee.tenman.portfolio.model.ReconciliationResult
import ee.tenman.portfolio.service.etf.HoldingReconciliationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

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
