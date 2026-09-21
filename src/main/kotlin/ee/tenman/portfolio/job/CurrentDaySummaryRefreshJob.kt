package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.service.pricing.InstrumentMinutePriceService
import ee.tenman.portfolio.service.summary.CurrentDaySummaryCacheService
import ee.tenman.portfolio.service.summary.IntradaySummaryService
import ee.tenman.portfolio.service.summary.PlatformSummaryCacheService
import ee.tenman.portfolio.service.transaction.TransactionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

@ScheduledJob
class CurrentDaySummaryRefreshJob(
  private val currentDaySummaryCacheService: CurrentDaySummaryCacheService,
  private val platformSummaryCacheService: PlatformSummaryCacheService,
  private val intradaySummaryService: IntradaySummaryService,
  private val transactionService: TransactionService,
  private val instrumentMinutePriceService: InstrumentMinutePriceService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(fixedDelayString = "\${scheduling.jobs.summary-interval:60000}")
  fun refresh() {
    runCatching { instrumentMinutePriceService.record() }
      .onFailure { log.warn("Failed to record instrument minute prices", it) }
    val summary =
      runCatching { currentDaySummaryCacheService.refreshCurrentDaySummary() }
        .onFailure { log.warn("Failed to refresh current day summary cache", it) }
        .getOrNull() ?: return
    runCatching { intradaySummaryService.record(summary) }
      .onFailure { log.warn("Failed to record intraday summary snapshot", it) }
    runCatching { refreshKnownPlatforms(summary) }
      .onFailure { log.warn("Failed to refresh platform current day summaries", it) }
  }

  private fun refreshKnownPlatforms(summary: PortfolioDailySummary) {
    val platforms = transactionService.getDistinctPlatforms()
    if (platforms.isEmpty()) return
    platformSummaryCacheService.putCurrentDaySummaryForPlatforms(platforms, summary)
    platforms.forEach { platform ->
      runCatching { record(platform) }
        .onFailure { log.warn("Failed to record intraday summary for platform $platform", it) }
    }
  }

  private fun record(platform: Platform) {
    val summary = platformSummaryCacheService.refreshCurrentDaySummaryForPlatforms(listOf(platform))
    intradaySummaryService.record(summary, platform)
  }
}
