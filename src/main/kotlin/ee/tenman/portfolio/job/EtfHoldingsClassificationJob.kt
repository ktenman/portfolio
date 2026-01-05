package ee.tenman.portfolio.job

import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.model.ClassificationResult
import ee.tenman.portfolio.openrouter.OpenRouterCircuitBreaker
import ee.tenman.portfolio.service.etf.EtfHoldingPersistenceService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.integration.IndustryClassificationService
import ee.tenman.portfolio.service.integration.SectorClassificationInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

@ScheduledJob
class EtfHoldingsClassificationJob(
  private val etfHoldingPersistenceService: EtfHoldingPersistenceService,
  private val industryClassificationService: IndustryClassificationService,
  private val jobExecutionService: JobExecutionService,
  private val circuitBreaker: OpenRouterCircuitBreaker,
  private val properties: IndustryClassificationProperties,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private const val BATCH_SIZE = 100
  }

  @Scheduled(initialDelay = 180000, fixedDelay = Long.MAX_VALUE)
  @Scheduled(cron = "\${scheduling.jobs.etf-holdings-classification-cron:0 0 3 * * *}")
  fun runJob() {
    log.info("Running ETF holdings classification job")
    jobExecutionService.executeJob(this)
    log.info("Completed ETF holdings classification job")
  }

  override fun execute() {
    log.info("Starting ETF sector classification with batch size $BATCH_SIZE")
    val holdingIds = etfHoldingPersistenceService.findUnclassifiedHoldingIds()
    if (holdingIds.isEmpty()) {
      log.info("No unclassified holdings found")
      return
    }
    log.info("Found ${holdingIds.size} holdings without sector classification")
    val holdings = loadHoldingsMap(holdingIds)
    log.info("Loaded ${holdings.size} holdings")
    val result = processInBatches(holdingIds, holdings)
    log.info("Sector classification done: ${result.success} ok, ${result.failure} failed, ${result.skipped} skipped")
  }

  private fun loadHoldingsMap(holdingIds: List<Long>): Map<Long, EtfHolding> =
    etfHoldingPersistenceService
      .findAllByIds(holdingIds)
      .mapNotNull { holding -> holding.id?.let { it to holding } }
      .toMap()

  private fun processInBatches(
    holdingIds: List<Long>,
    holdings: Map<Long, EtfHolding>,
  ): ClassificationResult {
    val batches = holdingIds.chunked(BATCH_SIZE)
    var totalSuccess = 0
    var totalFailure = 0
    var totalSkipped = 0
    batches.forEachIndexed { batchIndex, batchIds ->
      log.info("Processing batch ${batchIndex + 1}/${batches.size} (${batchIds.size} holdings)")
      waitForRateLimit()
      val batchResult = processBatch(batchIds, holdings)
      totalSuccess += batchResult.success
      totalFailure += batchResult.failure
      totalSkipped += batchResult.skipped
      log.info("Batch ${batchIndex + 1} complete: ${batchResult.success} ok, ${batchResult.failure} failed, ${batchResult.skipped} skipped")
    }
    return ClassificationResult(success = totalSuccess, failure = totalFailure, skipped = totalSkipped)
  }

  private fun waitForRateLimit() {
    runBlocking {
      val waitTime = circuitBreaker.getWaitTimeMs(circuitBreaker.isUsingFallback())
      if (waitTime > 0) {
        log.debug("Rate limit wait: ${waitTime}ms")
        delay(waitTime + properties.rateLimitBufferMs)
      }
    }
  }

  private fun processBatch(
    batchIds: List<Long>,
    holdings: Map<Long, EtfHolding>,
  ): ClassificationResult {
    val inputs = mutableListOf<SectorClassificationInput>()
    val skippedIds = mutableListOf<Long>()
    batchIds.forEach { holdingId ->
      val holding = holdings[holdingId]
      if (holding == null || holding.name.isBlank()) {
        skippedIds.add(holdingId)
        return@forEach
      }
      inputs.add(SectorClassificationInput(holdingId = holdingId, name = holding.name, ticker = holding.ticker))
    }
    if (inputs.isEmpty()) {
      return ClassificationResult(success = 0, failure = 0, skipped = skippedIds.size)
    }
    val results = industryClassificationService.classifyBatch(inputs)
    var successCount = 0
    var failureCount = 0
    inputs.forEach { input ->
      val result = results[input.holdingId]
      if (result != null) {
        etfHoldingPersistenceService.updateSector(input.holdingId, result.sector.displayName, result.model)
        successCount++
      } else {
        failureCount++
      }
    }
    return ClassificationResult(success = successCount, failure = failureCount, skipped = skippedIds.size)
  }
}
