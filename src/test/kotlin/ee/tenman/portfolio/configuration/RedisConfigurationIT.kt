package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.lightyear.LightyearUuidCacheService
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test

@IntegrationTest
class RedisConfigurationIT {
  @Resource
  private lateinit var lightyearUuidCacheService: LightyearUuidCacheService

  @Test
  fun `should read a cached value immediately after writing it`() {
    val misses =
      (1..500).count {
        lightyearUuidCacheService.cacheUuid("ÕIE$it:XNAS:USD", "uuid-$it")
        lightyearUuidCacheService.getCachedUuid("ÕIE$it:XNAS:USD") == null
      }
    expect(misses).toEqual(0)
  }
}
