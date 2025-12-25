package ee.tenman.portfolio.lightyear

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import java.util.UUID

@IntegrationTest
@Isolated
class LightyearUuidCacheServiceTest {
  @Resource
  private lateinit var cacheService: LightyearUuidCacheService

  private fun uniqueSymbol(): String = "TEST_${UUID.randomUUID()}:SYMBOL"

  @Test
  fun `getCachedUuid should return null when cache is empty`() {
    val result = cacheService.getCachedUuid(uniqueSymbol())

    expect(result).toEqual(null)
  }

  @Test
  fun `cacheUuid should store and return uuid`() {
    val uuid = "test-uuid-123"

    val result = cacheService.cacheUuid(uniqueSymbol(), uuid)

    expect(result).toEqual(uuid)
  }

  @Test
  fun `cached uuid should be retrievable after caching`() {
    val symbol = uniqueSymbol()
    val uuid = "cached-uuid-456"

    cacheService.cacheUuid(symbol, uuid)
    val cachedValue = cacheService.getCachedUuid(symbol)

    expect(cachedValue).toEqual(uuid)
  }

  @Test
  fun `cacheUuid should store different values for different symbols`() {
    val symbol1 = uniqueSymbol()
    val symbol2 = uniqueSymbol()

    cacheService.cacheUuid(symbol1, "uuid-1")
    cacheService.cacheUuid(symbol2, "uuid-2")

    expect(cacheService.getCachedUuid(symbol1)).toEqual("uuid-1")
    expect(cacheService.getCachedUuid(symbol2)).toEqual("uuid-2")
  }
}
