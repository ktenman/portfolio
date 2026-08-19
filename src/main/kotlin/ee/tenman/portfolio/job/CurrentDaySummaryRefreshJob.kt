package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.service.summary.CurrentDaySummaryCacheService
import ee.tenman.portfolio.service.summary.PlatformSummaryCacheService
import ee.tenman.portfolio.service.transaction.TransactionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

@ScheduledJob
class CurrentDaySummaryRefreshJob(
  private val currentDaySummaryCacheService: CurrentDaySummaryCacheService,
  private val platformSummaryCacheService: PlatformSummaryCacheService,
  private val transactionService: TransactionService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(fixedDelayString = "\${scheduling.jobs.summary-interval:60000}")
  fun refresh() {
    val summary =
      runCatching { currentDaySummaryCacheService.refreshCurrentDaySummary() }
        .onFailure { log.warn("Failed to refresh current day summary cache", it) }
        .getOrNull() ?: return
    runCatching { cacheForKnownPlatforms(summary) }
      .onFailure { log.warn("Failed to refresh platform current day summary cache", it) }
  }

  private fun cacheForKnownPlatforms(summary: PortfolioDailySummary) {
    val platforms = transactionService.getDistinctPlatforms()
    if (platforms.isEmpty()) return
    platformSummaryCacheService.putCurrentDaySummaryForPlatforms(platforms, summary)
  }
}
