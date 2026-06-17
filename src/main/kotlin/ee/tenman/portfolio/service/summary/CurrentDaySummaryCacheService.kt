package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.domain.PortfolioDailySummary
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class CurrentDaySummaryCacheService(
  private val summaryService: SummaryService,
) {
  @Cacheable(value = [SUMMARY_CACHE], key = "'current-day-summary'")
  fun getCurrentDaySummary(): PortfolioDailySummary = summaryService.getCurrentDaySummary()

  @CachePut(value = [SUMMARY_CACHE], key = "'current-day-summary'")
  fun refreshCurrentDaySummary(): PortfolioDailySummary = summaryService.getCurrentDaySummary()
}
