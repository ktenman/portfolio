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
    cacheConfigurations[INSTRUMENT_CACHE] = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(1))
    cacheConfigurations[SUMMARY_CACHE] = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(3))
    cacheConfigurations[TRANSACTION_CACHE] =
      RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(1))
    cacheConfigurations[ETF_LOGOS_CACHE] = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofDays(365))
    cacheConfigurations[EASTER_HOLIDAYS_CACHE] = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofDays(365))
    cacheConfigurations[ETF_BREAKDOWN_CACHE] = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(2))
    cacheConfigurations[LIGHTYEAR_UUID_CACHE] = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofDays(1))
    cacheConfigurations[LOGO_CANDIDATES_CACHE] = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofDays(365))
    cacheConfigurations[LOGO_NAME_SEARCH_CACHE] = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofDays(1))
    cacheConfigurations[DIVERSIFICATION_ETFS_CACHE] = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(1))
    cacheConfigurations[DIVERSIFICATION_CONFIG_CACHE] = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofDays(1))
    val defaultConfig = RedisCacheConfiguration.defaultCacheConfig().entryTtl(DEFAULT_TTL)
    return RedisCacheManager
      .builder(connectionFactory)
      .cacheDefaults(defaultConfig)
      .withInitialCacheConfigurations(cacheConfigurations)
      .build()
  }

  companion object {
    const val INSTRUMENT_CACHE = "instrument-cache-v2"
    const val SUMMARY_CACHE = "summary-cache-v2"
    const val TRANSACTION_CACHE = "transaction-cache-v2"
    const val ONE_DAY_CACHE: String = "one-day-cache-v2"
    const val ETF_LOGOS_CACHE: String = "etf-logos-v2"
    const val ETF_BREAKDOWN_CACHE: String = "etf:breakdown"
    const val EASTER_HOLIDAYS_CACHE: String = "easter-holidays"
    const val LIGHTYEAR_UUID_CACHE: String = "lightyear-uuid"
    const val LOGO_CANDIDATES_CACHE: String = "logo-candidates"
    const val LOGO_NAME_SEARCH_CACHE: String = "logo-name-search"
    const val DIVERSIFICATION_ETFS_CACHE: String = "diversification-etfs"
    const val DIVERSIFICATION_CONFIG_CACHE: String = "diversification-config"
    private val DEFAULT_TTL: Duration = Duration.ofMinutes(5)
  }
}
