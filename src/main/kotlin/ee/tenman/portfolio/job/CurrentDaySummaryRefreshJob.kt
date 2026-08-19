package ee.tenman.portfolio.job

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

  @Scheduled(fixedDelayString = "\${scheduling.jobs.summary-interval:120000}")
  fun refresh() {
    runCatching { currentDaySummaryCacheService.refreshCurrentDaySummary() }
      .onFailure { log.warn("Failed to refresh current day summary cache", it) }
    runCatching { refreshForKnownPlatforms() }
      .onFailure { log.warn("Failed to refresh platform current day summary cache", it) }
  }

  private fun refreshForKnownPlatforms() {
    val platforms = transactionService.getDistinctPlatforms()
    if (platforms.isEmpty()) return
    platformSummaryCacheService.refreshCurrentDaySummaryForPlatforms(platforms)
  }
}
