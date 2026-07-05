package ee.tenman.portfolio.configuration

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.HOLDING_IDENTITY_CACHE
import ee.tenman.portfolio.openrouter.OpenRouterClient
import ee.tenman.portfolio.service.etf.HoldingIdentityService
import io.mockk.mockk
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@EnableCaching
@Profile("holding-identity-cache-test")
class HoldingIdentityCacheTestConfiguration {
  @Bean
  fun testCacheManager(): CacheManager = ConcurrentMapCacheManager(HOLDING_IDENTITY_CACHE)

  @Bean
  fun openRouterClient(): OpenRouterClient = mockk()

  @Bean
  fun holdingIdentityService(openRouterClient: OpenRouterClient): HoldingIdentityService =
    HoldingIdentityService(openRouterClient, IndustryClassificationProperties(enabled = true))
}
