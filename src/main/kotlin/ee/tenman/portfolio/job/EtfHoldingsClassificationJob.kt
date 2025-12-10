package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.model.ClassificationOutcome
import ee.tenman.portfolio.model.ClassificationResult
import ee.tenman.portfolio.openrouter.OpenRouterCircuitBreaker
import ee.tenman.portfolio.service.etf.EtfHoldingPersistenceService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.integration.IndustryClassificationService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

@ScheduledJob
class EtfHoldingsClassificationJob(
  private val etfHoldingPersistenceService: EtfHoldingPersistenceService,
  private val industryClassificationService: IndustryClassificationService,
  private val jobExecutionService: JobExecutionService,
  private val circuitBreaker: OpenRouterCircuitBreaker,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private const val PROGRESS_LOG_INTERVAL = 50
    private const val RATE_LIMIT_BUFFER_MS = 100L
  }

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
    holdingIds.forEachIndexed { index, holdingId ->
      val waitTime = circuitBreaker.getWaitTimeMs(circuitBreaker.isUsingFallback())
      if (waitTime > 0) {
        Thread.sleep(waitTime + RATE_LIMIT_BUFFER_MS)
      }
      when (classifyHolding(holdingId)) {
        ClassificationOutcome.SUCCESS -> successCount++
        ClassificationOutcome.FAILURE -> failureCount++
        ClassificationOutcome.SKIPPED -> skippedCount++
      }
      if ((index + 1) % PROGRESS_LOG_INTERVAL == 0) {
        log.info("Progress: ${index + 1}/${holdingIds.size} processed")
      }
    }
    return ClassificationResult(success = successCount, failure = failureCount, skipped = skippedCount)
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
    val result =
      industryClassificationService.classifyCompanyWithModel(holding.name) ?: run {
        log.warn("Classification returned null for: ${holding.name}")
        return ClassificationOutcome.FAILURE
      }
    etfHoldingPersistenceService.updateSector(holdingId, result.sector.displayName, result.model)
    log.info("Successfully classified '${holding.name}' as '${result.sector.displayName}' using model ${result.model}")
    return ClassificationOutcome.SUCCESS
  }
}
