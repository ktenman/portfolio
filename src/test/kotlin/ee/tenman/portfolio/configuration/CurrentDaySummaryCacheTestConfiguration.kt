package ee.tenman.portfolio.configuration

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.service.summary.CurrentDaySummaryCacheService
import ee.tenman.portfolio.service.summary.SummaryService
import io.mockk.mockk
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@EnableCaching
@Profile("summary-cache-unit-test")
class CurrentDaySummaryCacheTestConfiguration {
  @Bean
  fun summaryService(): SummaryService = mockk()

  @Bean
  fun testCacheManager(): CacheManager = ConcurrentMapCacheManager(SUMMARY_CACHE)

  @Bean
  fun currentDaySummaryCacheService(summaryService: SummaryService): CurrentDaySummaryCacheService =
    CurrentDaySummaryCacheService(summaryService)
}
