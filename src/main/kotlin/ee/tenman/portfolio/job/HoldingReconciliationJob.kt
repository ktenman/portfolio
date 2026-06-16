package ee.tenman.portfolio.job

import ee.tenman.portfolio.configuration.HoldingReconciliationProperties
import ee.tenman.portfolio.service.etf.HoldingReconciliationService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

@ScheduledJob
class HoldingReconciliationJob(
  private val holdingReconciliationService: HoldingReconciliationService,
  private val properties: HoldingReconciliationProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "\${scheduling.jobs.holding-reconciliation-cron:0 0 3 1 1,4,7,10 *}")
  fun runJob() {
    if (!properties.enabled) return
    log.info("Running holding reconciliation job (dryRun=${properties.dryRun})")
    val result = holdingReconciliationService.reconcile(properties.dryRun)
    log.info(
      "Holding reconciliation done: ${result.mergedGroups} groups, ${result.mergedDuplicates} duplicates (dryRun=${properties.dryRun})",
    )
  }
}
