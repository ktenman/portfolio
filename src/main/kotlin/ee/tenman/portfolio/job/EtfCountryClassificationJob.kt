package ee.tenman.portfolio.job

import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.model.ClassificationOutcome
import ee.tenman.portfolio.model.ClassificationResult
import ee.tenman.portfolio.openrouter.OpenRouterCircuitBreaker
import ee.tenman.portfolio.service.etf.EtfHoldingPersistenceService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.integration.CountryClassificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.atomic.AtomicInteger

@ScheduledJob
class EtfCountryClassificationJob(
  private val etfHoldingPersistenceService: EtfHoldingPersistenceService,
  private val countryClassificationService: CountryClassificationService,
  private val jobExecutionService: JobExecutionService,
  private val circuitBreaker: OpenRouterCircuitBreaker,
  private val properties: IndustryClassificationProperties,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private const val PROGRESS_LOG_INTERVAL = 50
    private const val PARALLEL_THREADS = 5
  }

  @Scheduled(initialDelay = 120000, fixedDelay = Long.MAX_VALUE)
  @Scheduled(cron = "\${scheduling.jobs.etf-country-classification-cron:0 30 3 * * *}")
  fun runJob() {
    log.info("Running ETF country classification job")
    jobExecutionService.executeJob(this)
    log.info("Completed ETF country classification job")
  }

  override fun execute() {
    log.info("Starting ETF country classification with $PARALLEL_THREADS parallel threads")
    val holdingIds = etfHoldingPersistenceService.findUnclassifiedByCountryHoldingIds()
    if (holdingIds.isEmpty()) {
      log.info("No unclassified holdings by country found")
      return
    }
    log.info("Found ${holdingIds.size} holdings without country classification")
    val holdings =
      etfHoldingPersistenceService
        .findAllByIds(holdingIds)
        .mapNotNull { holding -> holding.id?.let { it to holding } }
        .toMap()
    val etfNamesMap = etfHoldingPersistenceService.findEtfNamesForHoldings(holdingIds)
    log.info("Batch loaded ${holdings.size} holdings and ETF names")
    val result = processHoldingsInParallel(holdingIds, holdings, etfNamesMap)
    log.info("Country classification done: ${result.success} ok, ${result.failure} failed, ${result.skipped} skipped")
  }

  private fun processHoldingsInParallel(
    holdingIds: List<Long>,
    holdings: Map<Long, EtfHolding>,
    etfNamesMap: Map<Long, List<String>>,
  ): ClassificationResult {
    val chunks = holdingIds.chunked((holdingIds.size + PARALLEL_THREADS - 1) / PARALLEL_THREADS)
    val processedCount = AtomicInteger(0)
    val totalCount = holdingIds.size
    val results =
      runBlocking {
        chunks
          .map { chunk ->
            async(Dispatchers.IO) {
              processChunk(chunk, holdings, etfNamesMap, processedCount, totalCount)
            }
          }.awaitAll()
      }
    return results.fold(ClassificationResult(0, 0, 0)) { acc, result ->
      ClassificationResult(
        success = acc.success + result.success,
        failure = acc.failure + result.failure,
        skipped = acc.skipped + result.skipped,
      )
    }
  }

  private suspend fun processChunk(
    holdingIds: List<Long>,
    holdings: Map<Long, EtfHolding>,
    etfNamesMap: Map<Long, List<String>>,
    processedCount: AtomicInteger,
    totalCount: Int,
  ): ClassificationResult {
    var successCount = 0
    var failureCount = 0
    var skippedCount = 0
    holdingIds.forEach { holdingId ->
      val waitTime = circuitBreaker.getWaitTimeMs(circuitBreaker.isUsingFallback())
      if (waitTime > 0) {
        delay(waitTime + properties.rateLimitBufferMs)
      }
      val holding = holdings[holdingId]
      val etfNames = etfNamesMap[holdingId] ?: emptyList()
      when (classifyHolding(holdingId, holding, etfNames)) {
        ClassificationOutcome.SUCCESS -> successCount++
        ClassificationOutcome.FAILURE -> failureCount++
        ClassificationOutcome.SKIPPED -> skippedCount++
      }
      val processed = processedCount.incrementAndGet()
      if (processed % PROGRESS_LOG_INTERVAL == 0) {
        log.info("Progress: $processed/$totalCount processed")
      }
    }
    return ClassificationResult(success = successCount, failure = failureCount, skipped = skippedCount)
  }

  private fun classifyHolding(
    holdingId: Long,
    holding: EtfHolding?,
    etfNames: List<String>,
  ): ClassificationOutcome =
    runCatching {
      if (holding == null) return ClassificationOutcome.SKIPPED
      classifyAndSave(holding, etfNames)
    }.getOrElse { e ->
      log.error("Error classifying country for holding id=$holdingId", e)
      ClassificationOutcome.FAILURE
    }

  private fun classifyAndSave(
    holding: EtfHolding,
    etfNames: List<String>,
  ): ClassificationOutcome {
    if (holding.name.isBlank()) {
      log.warn("Skipping holding with blank name: id=${holding.id}")
      return ClassificationOutcome.SKIPPED
    }
    val holdingId =
      holding.id ?: run {
        log.warn("Skipping holding with null id: name=${holding.name}")
        return ClassificationOutcome.SKIPPED
      }
    log.info("Classifying country for: ${holding.name} (ticker: ${holding.ticker}, ETFs: ${etfNames.joinToString(", ")})")
    val result =
      countryClassificationService.classifyCompanyCountryWithModel(holding.name, holding.ticker, etfNames) ?: run {
        log.warn("Country classification returned null for: ${holding.name}")
        return ClassificationOutcome.FAILURE
      }
    etfHoldingPersistenceService.updateCountry(holdingId, result.countryCode, result.countryName, result.model)
    log.info(
      "Successfully classified '${holding.name}' as '${result.countryName}' (${result.countryCode}) using model ${result.model?.modelId}",
    )
    return ClassificationOutcome.SUCCESS
  }
}
