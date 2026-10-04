package ee.tenman.portfolio.service.infrastructure

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.DIVERSIFICATION_ETFS_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ETF_BREAKDOWN_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ONE_DAY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.PLATFORM_SUMMARY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class CacheInvalidationService(
  private val cacheManager: CacheManager,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun evictInstrumentCaches(
    instrumentId: Long?,
    symbol: String?,
  ) = evict(INSTRUMENT_CACHE) { cache -> listOfNotNull(instrumentId, symbol, ALL_INSTRUMENTS_KEY).forEach(cache::evict) }

  fun evictTransactionCaches() = evict(TRANSACTION_CACHE, Cache::clear)

  fun evictSummaryCaches() {
    evict(SUMMARY_CACHE, Cache::clear)
    evict(PLATFORM_SUMMARY_CACHE, Cache::clear)
  }

  fun evictXirrCache() = evict(ONE_DAY_CACHE) { it.evict(XIRR_KEY) }

  fun evictAllRelatedCaches(
    instrumentId: Long?,
    symbol: String?,
  ) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) return evictRelated(instrumentId, symbol)
    TransactionSynchronizationManager.registerSynchronization(
      object : TransactionSynchronization {
        override fun afterCommit() = evictRelated(instrumentId, symbol)
      },
    )
  }

  fun evictEtfBreakdownCache() = evict(ETF_BREAKDOWN_CACHE, Cache::clear)

  fun evictDiversificationEtfsCache() = evict(DIVERSIFICATION_ETFS_CACHE, Cache::clear)

  private fun evictRelated(
    instrumentId: Long?,
    symbol: String?,
  ) {
    evictInstrumentCaches(instrumentId, symbol)
    evictTransactionCaches()
    evictSummaryCaches()
    evictXirrCache()
  }

  private fun evict(
    cacheName: String,
    eviction: (Cache) -> Unit,
  ) {
    val cache = cacheManager.getCache(cacheName) ?: return
    runCatching { eviction(cache) }
      .onSuccess { log.debug("Evicted cache $cacheName") }
      .onFailure { log.warn("Failed to evict cache $cacheName", it) }
  }

  companion object {
    private const val ALL_INSTRUMENTS_KEY = "allInstruments"
    private const val XIRR_KEY = "xirr-v3"
  }
}
