package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.radio.RadioMetricsService
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class RadioMonitoringJob(
  private val radioMetricsService: RadioMetricsService,
) {
  @Scheduled(fixedDelay = 60000, initialDelay = 0)
  fun refresh() {
    radioMetricsService.refresh()
  }
}
