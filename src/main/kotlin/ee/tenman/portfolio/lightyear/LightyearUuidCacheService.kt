package ee.tenman.portfolio.lightyear

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.LIGHTYEAR_UUID_CACHE
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class LightyearUuidCacheService {
  @Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
  @Cacheable(value = [LIGHTYEAR_UUID_CACHE], key = "#symbol", unless = "#result == null")
  fun getCachedUuid(symbol: String): String? = null

  @Suppress("UnusedParameter")
  @CachePut(value = [LIGHTYEAR_UUID_CACHE], key = "#symbol")
  fun cacheUuid(
    symbol: String,
    uuid: String,
  ): String = uuid
}
