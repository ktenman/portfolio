package ee.tenman.portfolio.configuration

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.PLATFORM_SUMMARY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.service.summary.CurrentDaySummaryCacheService
import ee.tenman.portfolio.service.summary.PlatformSummaryCacheService
import ee.tenman.portfolio.service.summary.SummaryService
import io.mockk.mockk
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.Clock

@Configuration
@EnableCaching
@Profile("summary-cache-unit-test")
class CurrentDaySummaryCacheTestConfiguration {
  @Bean
  fun summaryService(): SummaryService = mockk()

  @Bean
  fun clock(): Clock = mockk()

  @Bean
  fun testCacheManager(): CacheManager = ConcurrentMapCacheManager(SUMMARY_CACHE, PLATFORM_SUMMARY_CACHE)

  @Bean
  fun currentDaySummaryCacheService(summaryService: SummaryService): CurrentDaySummaryCacheService =
    CurrentDaySummaryCacheService(summaryService)

  @Bean
  fun platformSummaryCacheService(
    summaryService: SummaryService,
    clock: Clock,
  ): PlatformSummaryCacheService = PlatformSummaryCacheService(summaryService, clock)
}
