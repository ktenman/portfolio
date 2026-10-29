package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.split.SplitMetricsService
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class SplitMonitoringJob(
  private val splitMetricsService: SplitMetricsService,
) {
  @Scheduled(fixedDelay = 60000, initialDelay = 0)
  fun refresh() {
    splitMetricsService.refresh()
  }
}
