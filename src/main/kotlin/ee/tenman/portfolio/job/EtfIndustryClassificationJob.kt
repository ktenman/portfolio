package ee.tenman.portfolio.job

import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.model.ClassificationResult
import ee.tenman.portfolio.openrouter.OpenRouterCircuitBreaker
import ee.tenman.portfolio.service.etf.EtfHoldingIndustryService
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
    if (!properties.enabled || !properties.gicsEnabled) {
      log.info("GICS industry classification disabled, skipping job")
      return
    }
    val holdings = etfHoldingIndustryService.findUnclassifiedByIndustry()
    if (holdings.isEmpty()) {
      log.info("No holdings without industry classification found")
      return
    }
    log.info("Found ${holdings.size} holdings without industry classification")
    val result = processInBatches(holdings)
    if (result.success > 0) {
      cacheInvalidationService.evictEtfBreakdownCache()
      cacheInvalidationService.evictDiversificationEtfsCache()
    }
    result.requireAnySuccess("Industry")
    log.info("Industry classification done: ${result.success} ok, ${result.failure} failed, ${result.skipped} skipped")
  }

  private fun processInBatches(holdings: List<EtfHolding>): ClassificationResult {
    val batches = holdings.chunked(BATCH_SIZE)
    return batches
      .mapIndexed { index, batch ->
        log.info("Processing industry batch ${index + 1}/${batches.size} (${batch.size} holdings)")
        waitForRateLimit()
        processBatch(batch)
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

  private fun processBatch(batch: List<EtfHolding>): ClassificationResult {
    val inputs = batch.filter { it.name.isNotBlank() }.map { CompanyClassificationInput(it.id, it.name, it.ticker) }
    val skipped = batch.size - inputs.size
    if (inputs.isEmpty()) return ClassificationResult(success = 0, failure = 0, skipped = skipped)
    val outcome = classificationService.classifyBatch(inputs)
    val (classified, missing) = inputs.partition { outcome.results.containsKey(it.holdingId) }
    classified.forEach { input ->
      val result = outcome.results.getValue(input.holdingId)
      etfHoldingIndustryService.updateIndustry(input.holdingId, result.industry, result.model)
    }
    if (outcome.llmAnswered) missing.forEach { etfHoldingIndustryService.incrementIndustryFetchAttempts(it.holdingId) }
    return ClassificationResult(success = classified.size, failure = missing.size, skipped = skipped)
  }

  private companion object {
    const val BATCH_SIZE = 100
  }
}
