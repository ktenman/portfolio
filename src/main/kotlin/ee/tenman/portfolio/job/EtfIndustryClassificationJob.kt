package ee.tenman.portfolio.job

import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.model.ClassificationResult
import ee.tenman.portfolio.openrouter.OpenRouterCircuitBreaker
import ee.tenman.portfolio.service.etf.EtfHoldingIndustryService
import ee.tenman.portfolio.service.etf.EtfHoldingPersistenceService
import ee.tenman.portfolio.service.infrastructure.CacheInvalidationService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.integration.CompanyClassificationInput
import ee.tenman.portfolio.service.integration.GicsIndustryClassificationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

@ScheduledJob
class EtfIndustryClassificationJob(
  private val etfHoldingPersistenceService: EtfHoldingPersistenceService,
  private val etfHoldingIndustryService: EtfHoldingIndustryService,
  private val classificationService: GicsIndustryClassificationService,
  private val jobExecutionService: JobExecutionService,
  private val circuitBreaker: OpenRouterCircuitBreaker,
  private val properties: IndustryClassificationProperties,
  private val cacheInvalidationService: CacheInvalidationService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = 240000, fixedDelay = Long.MAX_VALUE)
  @Scheduled(cron = "\${scheduling.jobs.etf-industry-classification-cron:0 0 4 * * SUN}")
  fun runJob() {
    log.info("Running ETF industry classification job")
    jobExecutionService.executeJob(this)
    log.info("Completed ETF industry classification job")
  }

  override fun execute() {
    if (!properties.enabled) {
      log.info("Industry classification disabled, skipping job")
      return
    }
    val holdingIds = etfHoldingIndustryService.findUnclassifiedByIndustryHoldingIds()
    if (holdingIds.isEmpty()) {
      log.info("No holdings without industry classification found")
      return
    }
    log.info("Found ${holdingIds.size} holdings without industry classification")
    val holdings = loadHoldingsMap(holdingIds)
    val result = processInBatches(holdingIds, holdings)
    if (result.success > 0) {
      cacheInvalidationService.evictEtfBreakdownCache()
      cacheInvalidationService.evictDiversificationEtfsCache()
    }
    result.requireAnySuccess("Industry")
    log.info("Industry classification done: ${result.success} ok, ${result.failure} failed, ${result.skipped} skipped")
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
    return batches
      .mapIndexed { index, batchIds ->
        log.info("Processing industry batch ${index + 1}/${batches.size} (${batchIds.size} holdings)")
        waitForRateLimit()
        processBatch(batchIds, holdings)
      }.fold(ClassificationResult(0, 0, 0)) { acc, batch ->
        ClassificationResult(acc.success + batch.success, acc.failure + batch.failure, acc.skipped + batch.skipped)
      }
  }

  private fun waitForRateLimit() {
    runBlocking {
      val waitTime = circuitBreaker.getWaitTimeMs(circuitBreaker.isUsingFallback())
      if (waitTime > 0) delay(waitTime + properties.rateLimitBufferMs)
    }
  }

  private fun processBatch(
    batchIds: List<Long>,
    holdings: Map<Long, EtfHolding>,
  ): ClassificationResult {
    val inputs =
      batchIds.mapNotNull { id ->
        holdings[id]?.takeIf { it.name.isNotBlank() }?.let { CompanyClassificationInput(id, it.name, it.ticker) }
      }
    val skipped = batchIds.size - inputs.size
    if (inputs.isEmpty()) return ClassificationResult(success = 0, failure = 0, skipped = skipped)
    val outcome = classificationService.classifyBatch(inputs)
    val classified =
      inputs.count { input ->
        val result = outcome.results[input.holdingId]
        if (result == null) {
          if (outcome.llmAnswered) etfHoldingIndustryService.incrementIndustryFetchAttempts(input.holdingId)
          return@count false
        }
        etfHoldingIndustryService.updateIndustry(input.holdingId, result.industry, result.model)
        true
      }
    return ClassificationResult(success = classified, failure = inputs.size - classified, skipped = skipped)
  }

  private companion object {
    const val BATCH_SIZE = 100
  }
}
