package ee.tenman.portfolio.lightyear

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.LIGHTYEAR_UUID_CACHE
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.cache.CacheManager

@IntegrationTest
class LightyearUuidCacheServiceTest {
  @Resource
  private lateinit var cacheService: LightyearUuidCacheService

  @Resource
  private lateinit var cacheManager: CacheManager

  @BeforeEach
  fun setUp() {
    cacheManager.getCache(LIGHTYEAR_UUID_CACHE)?.clear()
  }

  @Test
  fun `getCachedUuid should return null when cache is empty`() {
    val result = cacheService.getCachedUuid("UNKNOWN:SYMBOL")

    expect(result).toEqual(null)
  }

  @Test
  fun `cacheUuid should store and return uuid`() {
    val uuid = "test-uuid-123"

    val result = cacheService.cacheUuid("TEST:SYMBOL", uuid)

    expect(result).toEqual(uuid)
  }

  @Test
  fun `cacheUuid should put value in cache that can be retrieved via service`() {
    val symbol = "CACHED:SYMBOL:${System.nanoTime()}"
    val uuid = "cached-uuid-456"

    cacheService.cacheUuid(symbol, uuid)
    val cachedValue = cacheService.getCachedUuid(symbol)

    expect(cachedValue).toEqual(uuid)
  }

  @Test
  fun `cacheUuid should store different values for different symbols`() {
    val suffix = System.nanoTime()
    val symbol1 = "SYMBOL1:$suffix"
    val symbol2 = "SYMBOL2:$suffix"

    cacheService.cacheUuid(symbol1, "uuid-1")
    cacheService.cacheUuid(symbol2, "uuid-2")

    expect(cacheService.getCachedUuid(symbol1)).toEqual("uuid-1")
    expect(cacheService.getCachedUuid(symbol2)).toEqual("uuid-2")
  }
}
