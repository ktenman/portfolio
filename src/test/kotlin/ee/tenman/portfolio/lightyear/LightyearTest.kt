package ee.tenman.portfolio.lightyear

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.LightyearUuidCacheTestConfiguration
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.LIGHTYEAR_UUID_CACHE
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.cache.CacheManager
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.math.BigDecimal

class LightyearFundInfoResponseTest {
  private val mapper =
    JsonMapper
      .builder()
      .addModule(KotlinModule.Builder().build())
      .build()

  @Test
  fun `maps lightyear json baseCurrency to fundCurrency kotlin field`() {
    val json =
      """
      {"ter": 0.12, "aum": 14836.44, "aumCurrency": "EUR", "baseCurrency": "USD", "shareCurrency": "EUR"}
      """.trimIndent()
    val response = mapper.readValue(json, LightyearFundInfoResponse::class.java)
    expect(response.fundCurrency).toEqual("USD")
    expect(response.ter).notToEqualNull().toEqualNumerically(BigDecimal("0.12"))
    expect(response.aumCurrency).toEqual("EUR")
  }

  @Test
  fun `fundCurrency is null when baseCurrency missing from response`() {
    val json = """{"ter": 0.2}"""
    val response = mapper.readValue(json, LightyearFundInfoResponse::class.java)
    expect(response.fundCurrency).toEqual(null)
  }
}

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [LightyearUuidCacheTestConfiguration::class])
@ActiveProfiles("cache-unit-test")
class LightyearUuidCacheServiceTest {
  @Resource
  private lateinit var cacheService: LightyearUuidCacheService

  @Resource
  private lateinit var testCacheManager: CacheManager

  @BeforeEach
  fun setup() {
    testCacheManager.getCache(LIGHTYEAR_UUID_CACHE)?.clear()
  }

  @Test
  fun `getCachedUuid should return null when cache is empty`() {
    val result = cacheService.getCachedUuid("UNKNOWN:SYMBOL")

    expect(result).toEqual(null)
  }

  @Test
  fun `cacheUuid should store and return uuid`() {
    val uuid = "test-uuid-123"

    val result = cacheService.cacheUuid("AAPL:NASDAQ", uuid)

    expect(result).toEqual(uuid)
  }

  @Test
  fun `cached uuid should be retrievable after caching`() {
    val symbol = "GOOGL:NASDAQ"
    val uuid = "cached-uuid-456"

    cacheService.cacheUuid(symbol, uuid)
    val cachedValue = cacheService.getCachedUuid(symbol)

    expect(cachedValue).toEqual(uuid)
  }

  @Test
  fun `cacheUuid should store different values for different symbols`() {
    cacheService.cacheUuid("MSFT:NASDAQ", "uuid-1")
    cacheService.cacheUuid("AMZN:NASDAQ", "uuid-2")

    expect(cacheService.getCachedUuid("MSFT:NASDAQ")).toEqual("uuid-1")
    expect(cacheService.getCachedUuid("AMZN:NASDAQ")).toEqual("uuid-2")
  }

  @Test
  fun `cacheUuid should overwrite existing cached value`() {
    val symbol = "TSLA:NASDAQ"

    cacheService.cacheUuid(symbol, "first-uuid")
    cacheService.cacheUuid(symbol, "second-uuid")
    val cachedValue = cacheService.getCachedUuid(symbol)

    expect(cachedValue).toEqual("second-uuid")
  }
}
