package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.model.ClassificationOutcome
import ee.tenman.portfolio.model.ClassificationResult
import ee.tenman.portfolio.openrouter.OpenRouterCircuitBreaker
import ee.tenman.portfolio.service.etf.EtfHoldingPersistenceService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.integration.IndustryClassificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.atomic.AtomicInteger

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
    private const val PARALLEL_THREADS = 3
  }

  @Scheduled(initialDelay = 180000, fixedDelay = Long.MAX_VALUE)
  @Scheduled(cron = "\${scheduling.jobs.etf-holdings-classification-cron:0 0 3 * * *}")
  fun runJob() {
    log.info("Running ETF holdings classification job")
    jobExecutionService.executeJob(this)
    log.info("Completed ETF holdings classification job")
  }

  override fun execute() {
    log.info("Starting ETF holdings classification with $PARALLEL_THREADS parallel threads")
    val holdingIds = etfHoldingPersistenceService.findUnclassifiedHoldingIds()
    if (holdingIds.isEmpty()) {
      log.info("No unclassified holdings found")
      return
    }
    log.info("Found ${holdingIds.size} holdings without sector classification")
    val result = processHoldingsInParallel(holdingIds)
    log.info("Sector classification done: ${result.success} ok, ${result.failure} failed, ${result.skipped} skipped")
  }

  private fun processHoldingsInParallel(holdingIds: List<Long>): ClassificationResult {
    val chunks = holdingIds.chunked((holdingIds.size + PARALLEL_THREADS - 1) / PARALLEL_THREADS)
    val processedCount = AtomicInteger(0)
    val totalCount = holdingIds.size
    val results =
      runBlocking {
        chunks
          .map { chunk ->
            async(Dispatchers.IO) {
              processChunk(chunk, processedCount, totalCount)
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
    processedCount: AtomicInteger,
    totalCount: Int,
  ): ClassificationResult {
    var successCount = 0
    var failureCount = 0
    var skippedCount = 0
    holdingIds.forEach { holdingId ->
      val waitTime = circuitBreaker.getWaitTimeMs(circuitBreaker.isUsingFallback())
      if (waitTime > 0) {
        delay(waitTime + RATE_LIMIT_BUFFER_MS)
      }
      when (classifyHolding(holdingId)) {
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
