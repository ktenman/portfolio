package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.summary.CurrentDaySummaryCacheService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

@ScheduledJob
class CurrentDaySummaryRefreshJob(
  private val currentDaySummaryCacheService: CurrentDaySummaryCacheService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(fixedDelayString = "\${scheduling.jobs.summary-interval:120000}")
  fun refresh() {
    runCatching { currentDaySummaryCacheService.refreshCurrentDaySummary() }
      .onFailure { log.warn("Failed to refresh current day summary cache", it) }
  }
}
