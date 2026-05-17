package ee.tenman.portfolio.job

import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.openrouter.OpenRouterCircuitBreaker
import ee.tenman.portfolio.service.etf.EtfHoldingPersistenceService
import ee.tenman.portfolio.service.etf.HoldingDeduplicationService
import ee.tenman.portfolio.service.etf.HoldingMatchCandidate
import ee.tenman.portfolio.service.etf.HoldingSimilarityPrefilterService
import ee.tenman.portfolio.service.etf.MatchSource
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

@ScheduledJob
class HoldingDeduplicationJob(
  private val etfHoldingPersistenceService: EtfHoldingPersistenceService,
  private val prefilter: HoldingSimilarityPrefilterService,
  private val deduplicationService: HoldingDeduplicationService,
  private val jobExecutionService: JobExecutionService,
  private val circuitBreaker: OpenRouterCircuitBreaker,
  private val properties: IndustryClassificationProperties,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = STARTUP_DELAY_MS, fixedDelay = Long.MAX_VALUE)
  @Scheduled(cron = "\${scheduling.jobs.holding-deduplication-cron:0 0 4 * * *}")
  fun runJob() {
    log.info("Running holding deduplication job")
    jobExecutionService.executeJob(this)
    log.info("Completed holding deduplication job")
  }

  override fun execute() {
    val unlinked = etfHoldingPersistenceService.findCanonicalCandidateHoldings()
    if (unlinked.size < 2) {
      log.info("Not enough unlinked holdings for dedup: ${unlinked.size}")
      return
    }
    log.info("Building candidate pairs for ${unlinked.size} unlinked holdings")
    val candidates = prefilter.findCandidatePairs(unlinked)
    if (candidates.isEmpty()) {
      log.info("No candidate pairs from prefilter")
      return
    }
    log.info("Prefilter produced ${candidates.size} candidate pairs")
    val confirmed = confirmCandidates(candidates)
    log.info("Confirmed ${confirmed.size}/${candidates.size} pairs as duplicates")
    val links = deduplicationService.resolveCanonicalLinks(confirmed, unlinked)
    log.info("Resolved ${links.size} canonical links")
    persistLinks(links)
  }

  private fun confirmCandidates(candidates: List<HoldingMatchCandidate>): List<HoldingMatchCandidate> {
    val tickerCandidates = candidates.filter { it.source == MatchSource.TICKER }
    val nameCandidates = candidates.filter { it.source == MatchSource.NAME_SIMILARITY }
    val confirmedFromLlm = if (nameCandidates.isEmpty()) emptyList() else confirmNameBatches(nameCandidates)
    return tickerCandidates + confirmedFromLlm
  }

  private fun confirmNameBatches(candidates: List<HoldingMatchCandidate>): List<HoldingMatchCandidate> =
    candidates.chunked(BATCH_SIZE).flatMapIndexed { batchIndex, batch ->
      log.info("Confirming dedup batch ${batchIndex + 1} (${batch.size} pairs)")
      waitForRateLimit()
      deduplicationService.confirmDuplicates(batch)
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

  private fun persistLinks(links: Map<Long, Long>) {
    links.forEach { (duplicateId, canonicalId) ->
      etfHoldingPersistenceService.linkCanonical(duplicateId, canonicalId)
    }
  }

  companion object {
    private const val BATCH_SIZE = 100
    private const val STARTUP_DELAY_MS = 360000L
  }
}
