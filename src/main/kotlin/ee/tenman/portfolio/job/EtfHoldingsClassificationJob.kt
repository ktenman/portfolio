package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.IndustrySector
import ee.tenman.portfolio.model.ClassificationOutcome
import ee.tenman.portfolio.model.ClassificationResult
import ee.tenman.portfolio.service.EtfHoldingPersistenceService
import ee.tenman.portfolio.service.IndustryClassificationService
import ee.tenman.portfolio.service.JobExecutionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

@ScheduledJob
class EtfHoldingsClassificationJob(
  private val etfHoldingPersistenceService: EtfHoldingPersistenceService,
  private val industryClassificationService: IndustryClassificationService,
  private val jobExecutionService: JobExecutionService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = 180000, fixedDelay = Long.MAX_VALUE)
  @Scheduled(cron = "\${scheduling.jobs.etf-holdings-classification-cron:0 0 3 * * *}")
  fun runJob() {
    log.info("Running ETF holdings classification job")
    jobExecutionService.executeJob(this)
    log.info("Completed ETF holdings classification job")
  }

  override fun execute() {
    log.info("Starting ETF holdings classification")
    val holdingIds = etfHoldingPersistenceService.findUnclassifiedHoldingIds()
    if (holdingIds.isEmpty()) {
      log.info("No unclassified holdings found")
      return
    }
    log.info("Found ${holdingIds.size} holdings without sector classification")
    val (successCount, failureCount, skippedCount) = processHoldings(holdingIds)
    log.info(
      "Classification complete. Success: $successCount, Failed: $failureCount, Skipped: $skippedCount, Total: ${holdingIds.size}",
    )
  }

  private fun processHoldings(holdingIds: List<Long>): ClassificationResult {
    var successCount = 0
    var failureCount = 0
    var skippedCount = 0
    holdingIds.forEach { holdingId ->
      when (classifyHolding(holdingId)) {
        ClassificationOutcome.SUCCESS -> successCount++
        ClassificationOutcome.FAILURE -> failureCount++
        ClassificationOutcome.SKIPPED -> skippedCount++
      }
    }
    return ClassificationResult(successCount, failureCount, skippedCount)
  }

  private fun classifyHolding(holdingId: Long): ClassificationOutcome =
    runCatching {
      val holding =
        etfHoldingPersistenceService.findById(holdingId) ?: return ClassificationOutcome.SKIPPED
      classifyAndSave(holding)
    }.getOrElse { e ->
      log.error("Error classifying holding id=$holdingId", e)
      ClassificationOutcome.FAILURE
    }

  private fun classifyAndSave(holding: EtfHolding): ClassificationOutcome {
    if (holding.name.isBlank()) {
      log.warn("Skipping holding with blank name: id=${holding.id}")
      return ClassificationOutcome.SKIPPED
    }
    val holdingId =
      holding.id ?: run {
        log.warn("Skipping holding with null id: name=${holding.name}")
        return ClassificationOutcome.SKIPPED
      }
    log.info("Classifying: ${holding.name}")
    val sector: IndustrySector? = industryClassificationService.classifyCompany(holding.name)
    if (sector == null) {
      log.warn("Classification returned null for: ${holding.name}")
      return ClassificationOutcome.FAILURE
    }
    etfHoldingPersistenceService.updateSector(holdingId, sector.displayName)
    log.info("Successfully classified '${holding.name}' as '${sector.displayName}'")
    return ClassificationOutcome.SUCCESS
  }
}
