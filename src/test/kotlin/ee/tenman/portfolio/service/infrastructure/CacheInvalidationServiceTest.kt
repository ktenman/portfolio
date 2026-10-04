package ee.tenman.portfolio.service.infrastructure

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ETF_BREAKDOWN_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ONE_DAY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.transaction.support.TransactionSynchronizationManager

class CacheInvalidationServiceTest {
  private val cacheManager = mockk<CacheManager>()
  private val caches = mutableMapOf<String, Cache>()

  private lateinit var cacheInvalidationService: CacheInvalidationService

  @BeforeEach
  fun setup() {
    every { cacheManager.getCache(any()) } answers { cache(firstArg()) }

    cacheInvalidationService = CacheInvalidationService(cacheManager)
  }

  private fun cache(name: String): Cache = caches.getOrPut(name) { mockk(relaxed = true) }

  @Test
  fun `evictInstrumentCaches should evict instrument cache entries`() {
    cacheInvalidationService.evictInstrumentCaches(1L, "AAPL")

    verify { cacheManager.getCache(INSTRUMENT_CACHE) }
    verify { cache(INSTRUMENT_CACHE).evict(1L) }
    verify { cache(INSTRUMENT_CACHE).evict("AAPL") }
    verify { cache(INSTRUMENT_CACHE).evict("allInstruments") }
  }

  @Test
  fun `evictInstrumentCaches should handle null id`() {
    cacheInvalidationService.evictInstrumentCaches(null, "AAPL")

    verify { cache(INSTRUMENT_CACHE).evict("AAPL") }
    verify { cache(INSTRUMENT_CACHE).evict("allInstruments") }
    verify(exactly = 2) { cache(INSTRUMENT_CACHE).evict(any()) }
  }

  @Test
  fun `evictInstrumentCaches should handle null symbol`() {
    cacheInvalidationService.evictInstrumentCaches(1L, null)

    verify { cache(INSTRUMENT_CACHE).evict(1L) }
    verify { cache(INSTRUMENT_CACHE).evict("allInstruments") }
    verify(exactly = 2) { cache(INSTRUMENT_CACHE).evict(any()) }
  }

  @Test
  fun `evictTransactionCaches should clear the transaction cache`() {
    cacheInvalidationService.evictTransactionCaches()

    verify { cache(TRANSACTION_CACHE).clear() }
  }

  @Test
  fun `evictSummaryCaches should clear the summary cache`() {
    cacheInvalidationService.evictSummaryCaches()

    verify { cache(SUMMARY_CACHE).clear() }
  }

  @Test
  fun `evictXirrCache should evict xirr key from one day cache`() {
    cacheInvalidationService.evictXirrCache()

    verify { cache(ONE_DAY_CACHE).evict("xirr-v3") }
  }

  @Test
  fun `evictAllRelatedCaches should evict all cache types`() {
    cacheInvalidationService.evictAllRelatedCaches(1L, "AAPL")

    verify { cache(INSTRUMENT_CACHE).evict(1L) }
    verify { cache(TRANSACTION_CACHE).clear() }
    verify { cache(SUMMARY_CACHE).clear() }
    verify { cache(ONE_DAY_CACHE).evict("xirr-v3") }
  }

  @Test
  fun `evictAllRelatedCaches should wait for the commit inside a transaction`() {
    TransactionSynchronizationManager.initSynchronization()
    cacheInvalidationService.evictAllRelatedCaches(7L, "ÕUN")
    TransactionSynchronizationManager.clearSynchronization()

    verify(exactly = 0) { cache(SUMMARY_CACHE).clear() }
  }

  @Test
  fun `evictAllRelatedCaches should evict the instrument once the transaction commits`() {
    TransactionSynchronizationManager.initSynchronization()
    cacheInvalidationService.evictAllRelatedCaches(7L, "ÕUN")
    val synchronizations = TransactionSynchronizationManager.getSynchronizations()
    TransactionSynchronizationManager.clearSynchronization()
    synchronizations.forEach { it.afterCommit() }

    verify { cache(INSTRUMENT_CACHE).evict("ÕUN") }
  }

  @Test
  fun `evictAllRelatedCaches should keep evicting the other caches when Redis rejects one`() {
    every { cache(TRANSACTION_CACHE).clear() } throws RedisConnectionFailureException("Redis ühendus katkes")

    cacheInvalidationService.evictAllRelatedCaches(1L, "AAPL")

    verify { cache(SUMMARY_CACHE).clear() }
  }

  @Test
  fun `evictEtfBreakdownCache should clear the etf breakdown cache`() {
    cacheInvalidationService.evictEtfBreakdownCache()

    verify { cache(ETF_BREAKDOWN_CACHE).clear() }
  }

  @Test
  fun `evictInstrumentCaches should not fail when cache is null`() {
    every { cacheManager.getCache(INSTRUMENT_CACHE) } returns null

    cacheInvalidationService.evictInstrumentCaches(1L, "AAPL")

    verify { cacheManager.getCache(INSTRUMENT_CACHE) }
    verify(exactly = 0) { cache(INSTRUMENT_CACHE).evict(any()) }
  }
}

@IntegrationTest
class CacheInvalidationServiceIT {
  @Resource
  private lateinit var cacheInvalidationService: CacheInvalidationService

  @Resource
  private lateinit var cacheManager: CacheManager

  @Test
  fun `evictSummaryCaches should remove every cached summary from Redis`() {
    val cache = cacheManager.getCache(SUMMARY_CACHE)!!
    val keys = listOf("Ülevaade", "kokkuvõte")
    keys.forEach { cache.put(it, "väärtus") }

    cacheInvalidationService.evictSummaryCaches()

    expect(keys.mapNotNull { cache.get(it) }).toBeEmpty()
  }
}
