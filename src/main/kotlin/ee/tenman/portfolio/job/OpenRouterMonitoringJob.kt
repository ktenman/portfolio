package ee.tenman.portfolio.job

import ee.tenman.portfolio.openrouter.OpenRouterBalanceService
import ee.tenman.portfolio.openrouter.OpenRouterSpendService
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class OpenRouterMonitoringJob(
  private val openRouterBalanceService: OpenRouterBalanceService,
  private val openRouterSpendService: OpenRouterSpendService,
) {
  @Scheduled(fixedDelay = 300000, initialDelay = 0)
  fun refresh() {
    openRouterBalanceService.refresh()
    openRouterSpendService.refresh()
  }
}
