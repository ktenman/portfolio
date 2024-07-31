package ee.tenman.portfolio.configuration

import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import java.time.Duration

@Configuration
@EnableCaching
class RedisConfiguration {
  @Bean
  fun cacheManager(connectionFactory: RedisConnectionFactory): RedisCacheManager {
    val cacheConfigurations: MutableMap<String, RedisCacheConfiguration> = HashMap()
    cacheConfigurations[ONE_DAY_CACHE] = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofDays(1))
    cacheConfigurations[INSTRUMENT_CACHE] = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofDays(366))
    cacheConfigurations[THIRTY_MINUTES] = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(30))
    cacheConfigurations[SUMMARY_CACHE] = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(6))
    cacheConfigurations[SUMMARY_CACHE_15] = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(15))
    cacheConfigurations[TRANSACTION_CACHE] = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(1))
    val defaultConfig = RedisCacheConfiguration.defaultCacheConfig().entryTtl(DEFAULT_TTL)
    return RedisCacheManager.builder(connectionFactory)
      .cacheDefaults(defaultConfig)
      .withInitialCacheConfigurations(cacheConfigurations)
      .build()
  }

  companion object {
    const val INSTRUMENT_CACHE = "instrument-cache-v18"
    const val SUMMARY_CACHE = "summary-cache-v18"
    const val SUMMARY_CACHE_15 = "summary-cache-15-v18"
    const val TRANSACTION_CACHE = "transaction-cache-v18"
    const val ONE_DAY_CACHE: String = "one-day-cache-v18"
    const val THIRTY_MINUTES: String = "thirty-minutes-cache-v18"
    private val DEFAULT_TTL: Duration = Duration.ofMinutes(30)
  }
}
