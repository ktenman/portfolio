package ee.tenman.portfolio.configuration

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.LIGHTYEAR_UUID_CACHE
import ee.tenman.portfolio.lightyear.LightyearUuidCacheService
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@EnableCaching
@Profile("cache-unit-test")
class LightyearUuidCacheTestConfiguration {
  @Bean
  fun testCacheManager(): CacheManager = ConcurrentMapCacheManager(LIGHTYEAR_UUID_CACHE)

  @Bean
  fun lightyearUuidCacheService(): LightyearUuidCacheService = LightyearUuidCacheService()
}
