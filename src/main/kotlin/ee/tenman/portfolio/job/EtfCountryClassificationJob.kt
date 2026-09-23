package ee.tenman.portfolio.job

import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.model.ClassificationResult
import ee.tenman.portfolio.openrouter.OpenRouterCircuitBreaker
import ee.tenman.portfolio.service.etf.EtfHoldingCountryService
import ee.tenman.portfolio.service.etf.EtfHoldingPersistenceService
import ee.tenman.portfolio.service.infrastructure.CacheInvalidationService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.integration.CompanyClassificationInput
import ee.tenman.portfolio.service.integration.CountryClassificationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

@ScheduledJob
class EtfCountryClassificationJob(
  private val etfHoldingPersistenceService: EtfHoldingPersistenceService,
  private val etfHoldingCountryService: EtfHoldingCountryService,
  private val cacheInvalidationService: CacheInvalidationService,
  private val countryClassificationService: CountryClassificationService,
  private val jobExecutionService: JobExecutionService,
  private val circuitBreaker: OpenRouterCircuitBreaker,
  private val properties: IndustryClassificationProperties,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private const val BATCH_SIZE = 100
  }

  @Scheduled(cron = "\${scheduling.jobs.etf-country-classification-cron:0 30 4 * * *}")
  fun runJob() {
    log.info("Running ETF country classification job")
    jobExecutionService.executeJob(this)
    log.info("Completed ETF country classification job")
  }

  override fun execute() {
    if (!properties.enabled) {
      log.info("Country classification disabled, skipping job")
      return
    }
    log.info("Starting ETF country classification with batch size $BATCH_SIZE")
    val holdingIds = etfHoldingPersistenceService.findUnclassifiedByCountryHoldingIds()
    if (holdingIds.isEmpty()) {
      log.info("No unclassified holdings by country found")
      return
    }
    log.info("Found ${holdingIds.size} holdings without country classification")
    val holdings = loadHoldingsMap(holdingIds)
    val etfNamesMap = etfHoldingPersistenceService.findEtfNamesForHoldings(holdingIds)
    log.info("Loaded ${holdings.size} holdings and ETF names")
    val result = processInBatches(holdingIds, holdings, etfNamesMap)
    if (result.success > 0) {
      cacheInvalidationService.evictEtfBreakdownCache()
      cacheInvalidationService.evictDiversificationEtfsCache()
    }
    result.requireAnySuccess("Country")
    log.info("Country classification done: ${result.success} ok, ${result.failure} failed, ${result.skipped} skipped")
  }

  private fun loadHoldingsMap(holdingIds: List<Long>): Map<Long, EtfHolding> =
    etfHoldingPersistenceService
      .findAllByIds(holdingIds)
      .mapNotNull { holding -> holding.id?.let { it to holding } }
      .toMap()

  private fun processInBatches(
    holdingIds: List<Long>,
    holdings: Map<Long, EtfHolding>,
    etfNamesMap: Map<Long, List<String>>,
  ): ClassificationResult {
    val batches = holdingIds.chunked(BATCH_SIZE)
    var totalSuccess = 0
    var totalFailure = 0
    var totalSkipped = 0
    batches.forEachIndexed { batchIndex, batchIds ->
      log.info("Processing batch ${batchIndex + 1}/${batches.size} (${batchIds.size} holdings)")
      waitForRateLimit()
      val batchResult = processBatch(batchIds, holdings, etfNamesMap)
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
    etfNamesMap: Map<Long, List<String>>,
  ): ClassificationResult {
    val inputs = mutableListOf<CompanyClassificationInput>()
    val skippedIds = mutableListOf<Long>()
    batchIds.forEach { holdingId ->
      val holding = holdings[holdingId]
      if (holding == null || holding.name.isBlank() || countryClassificationService.isNonCompanyHolding(holding.name)) {
        skippedIds.add(holdingId)
        return@forEach
      }
      inputs.add(
        CompanyClassificationInput(
          holdingId = holdingId,
          name = holding.name,
          ticker = holding.ticker,
          etfNames = etfNamesMap[holdingId] ?: emptyList(),
        ),
      )
    }
    if (inputs.isEmpty()) {
      return ClassificationResult(success = 0, failure = 0, skipped = skippedIds.size)
    }
    val outcome = countryClassificationService.classifyBatch(inputs)
    val (classified, missing) = inputs.partition { outcome.results.containsKey(it.holdingId) }
    val success =
      classified.count { input ->
        val result = outcome.results.getValue(input.holdingId)
        etfHoldingCountryService.updateCountry(input.holdingId, result.countryCode, result.countryName, result.model)
      }
    if (outcome.llmAnswered) missing.forEach { etfHoldingPersistenceService.incrementCountryFetchAttempts(it.holdingId) }
    return ClassificationResult(success = success, failure = missing.size, skipped = skippedIds.size + classified.size - success)
  }
}
