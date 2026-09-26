package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.monitoring.CollectionMetricsService
import ee.tenman.portfolio.service.monitoring.CollectionStreamService
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class CollectionMonitoringJob(
  private val metrics: CollectionMetricsService,
  private val stream: CollectionStreamService,
) {
  @Scheduled(fixedDelay = 15000, initialDelay = 0)
  fun refresh() {
    metrics.refresh()
    stream.broadcast()
  }
}
