package ee.tenman.portfolio.lightyear

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate

@IntegrationTest
class LightyearUuidCacheServiceTest {
  @Resource
  private lateinit var cacheService: LightyearUuidCacheService

  @Resource
  private lateinit var stringRedisTemplate: StringRedisTemplate

  @BeforeEach
  fun setUp() {
    stringRedisTemplate.connectionFactory
      ?.connection
      ?.serverCommands()
      ?.flushAll()
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
  fun `cached uuid should be retrievable after caching`() {
    val symbol = "CACHED:SYMBOL"
    val uuid = "cached-uuid-456"

    cacheService.cacheUuid(symbol, uuid)
    val cachedValue = cacheService.getCachedUuid(symbol)

    expect(cachedValue).toEqual(uuid)
  }

  @Test
  fun `cacheUuid should store different values for different symbols`() {
    val symbol1 = "SYMBOL1:TEST"
    val symbol2 = "SYMBOL2:TEST"

    cacheService.cacheUuid(symbol1, "uuid-1")
    cacheService.cacheUuid(symbol2, "uuid-2")

    expect(cacheService.getCachedUuid(symbol1)).toEqual("uuid-1")
    expect(cacheService.getCachedUuid(symbol2)).toEqual("uuid-2")
  }
}
