package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ETF_BREAKDOWN_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ONE_DAY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class CacheInvalidationService(
  private val cacheManager: CacheManager,
  private val redisTemplate: StringRedisTemplate,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun evictInstrumentCaches(
    instrumentId: Long?,
    symbol: String?,
  ) {
    val cache = cacheManager.getCache(INSTRUMENT_CACHE) ?: return
    instrumentId?.let { cache.evict(it) }
    symbol?.let { cache.evict(it) }
    cache.evict(ALL_INSTRUMENTS_KEY)
    log.debug("Evicted instrument cache for id={}, symbol={}", instrumentId, symbol)
  }

  fun evictTransactionCaches() {
    evictCacheKeysByPattern(TRANSACTION_CACHE, "*")
    log.debug("Evicted all transaction caches")
  }

  fun evictSummaryCaches() {
    evictCacheKeysByPattern(SUMMARY_CACHE, "*")
    log.debug("Evicted all summary caches")
  }

  fun evictXirrCache() {
    cacheManager.getCache(ONE_DAY_CACHE)?.evict(XIRR_KEY)
    log.debug("Evicted XIRR cache")
  }

  fun evictAllRelatedCaches(
    instrumentId: Long?,
    symbol: String?,
  ) {
    evictInstrumentCaches(instrumentId, symbol)
    evictTransactionCaches()
    evictSummaryCaches()
    evictXirrCache()
  }

  fun evictEtfBreakdownCache() {
    cacheManager.getCache(ETF_BREAKDOWN_CACHE)?.clear()
    log.debug("Evicted ETF breakdown cache")
  }

  private fun evictCacheKeysByPattern(
    cacheName: String,
    pattern: String,
  ) {
    val keys = redisTemplate.keys("$cacheName::$pattern")
    if (keys.isNotEmpty()) {
      redisTemplate.delete(keys)
      log.debug("Evicted {} keys from cache {} matching pattern {}", keys.size, cacheName, pattern)
    }
  }

  companion object {
    private const val ALL_INSTRUMENTS_KEY = "allInstruments"
    private const val XIRR_KEY = "xirr-v3"
  }
}
