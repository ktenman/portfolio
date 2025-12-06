package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ETF_BREAKDOWN_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ONE_DAY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.data.redis.core.StringRedisTemplate

class CacheInvalidationServiceTest {
  private val cacheManager = mockk<CacheManager>()
  private val redisTemplate = mockk<StringRedisTemplate>()
  private val mockCache = mockk<Cache>(relaxed = true)

  private lateinit var cacheInvalidationService: CacheInvalidationService

  @BeforeEach
  fun setup() {
    every { cacheManager.getCache(any()) } returns mockCache
    every { redisTemplate.keys(any()) } returns emptySet()
    every { redisTemplate.delete(any<Set<String>>()) } returns 0L

    cacheInvalidationService = CacheInvalidationService(cacheManager, redisTemplate)
  }

  @Test
  fun `evictInstrumentCaches should evict instrument cache entries`() {
    cacheInvalidationService.evictInstrumentCaches(1L, "AAPL")

    verify { cacheManager.getCache(INSTRUMENT_CACHE) }
    verify { mockCache.evict(1L) }
    verify { mockCache.evict("AAPL") }
    verify { mockCache.evict("allInstruments") }
  }

  @Test
  fun `evictInstrumentCaches should handle null id`() {
    cacheInvalidationService.evictInstrumentCaches(null, "AAPL")

    verify { mockCache.evict("AAPL") }
    verify { mockCache.evict("allInstruments") }
    verify(exactly = 2) { mockCache.evict(any()) }
  }

  @Test
  fun `evictInstrumentCaches should handle null symbol`() {
    cacheInvalidationService.evictInstrumentCaches(1L, null)

    verify { mockCache.evict(1L) }
    verify { mockCache.evict("allInstruments") }
    verify(exactly = 2) { mockCache.evict(any()) }
  }

  @Test
  fun `evictTransactionCaches should evict transaction cache by pattern`() {
    every { redisTemplate.keys("$TRANSACTION_CACHE::*") } returns setOf("key1", "key2")
    every { redisTemplate.delete(setOf("key1", "key2")) } returns 2L

    cacheInvalidationService.evictTransactionCaches()

    verify { redisTemplate.keys("$TRANSACTION_CACHE::*") }
    verify { redisTemplate.delete(setOf("key1", "key2")) }
  }

  @Test
  fun `evictSummaryCaches should evict summary cache by pattern`() {
    every { redisTemplate.keys("$SUMMARY_CACHE::*") } returns setOf("summary1")
    every { redisTemplate.delete(setOf("summary1")) } returns 1L

    cacheInvalidationService.evictSummaryCaches()

    verify { redisTemplate.keys("$SUMMARY_CACHE::*") }
    verify { redisTemplate.delete(setOf("summary1")) }
  }

  @Test
  fun `evictXirrCache should evict xirr key from one day cache`() {
    cacheInvalidationService.evictXirrCache()

    verify { cacheManager.getCache(ONE_DAY_CACHE) }
    verify { mockCache.evict("xirr-v3") }
  }

  @Test
  fun `evictAllRelatedCaches should evict all cache types`() {
    cacheInvalidationService.evictAllRelatedCaches(1L, "AAPL")

    verify { cacheManager.getCache(INSTRUMENT_CACHE) }
    verify { redisTemplate.keys("$TRANSACTION_CACHE::*") }
    verify { redisTemplate.keys("$SUMMARY_CACHE::*") }
    verify { cacheManager.getCache(ONE_DAY_CACHE) }
  }

  @Test
  fun `evictEtfBreakdownCache should evict etf breakdown cache by pattern`() {
    every { redisTemplate.keys("$ETF_BREAKDOWN_CACHE::*") } returns setOf("etf1", "etf2")
    every { redisTemplate.delete(setOf("etf1", "etf2")) } returns 2L

    cacheInvalidationService.evictEtfBreakdownCache()

    verify { redisTemplate.keys("$ETF_BREAKDOWN_CACHE::*") }
    verify { redisTemplate.delete(setOf("etf1", "etf2")) }
  }

  @Test
  fun `evictTransactionCaches should not delete when no keys found`() {
    every { redisTemplate.keys("$TRANSACTION_CACHE::*") } returns emptySet()

    cacheInvalidationService.evictTransactionCaches()

    verify { redisTemplate.keys("$TRANSACTION_CACHE::*") }
    verify(exactly = 0) { redisTemplate.delete(any<Set<String>>()) }
  }

  @Test
  fun `evictInstrumentCaches should not fail when cache is null`() {
    every { cacheManager.getCache(INSTRUMENT_CACHE) } returns null

    cacheInvalidationService.evictInstrumentCaches(1L, "AAPL")

    expect(true).toEqual(true)
  }
}
