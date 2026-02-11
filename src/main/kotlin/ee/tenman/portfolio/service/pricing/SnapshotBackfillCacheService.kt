package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SNAPSHOT_BACKFILL_CACHE
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class SnapshotBackfillCacheService {
  @Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
  @Cacheable(value = [SNAPSHOT_BACKFILL_CACHE], key = "#instrumentId", unless = "#result == null")
  fun isBackfilled(instrumentId: Long): Boolean? = null

  @Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
  @CachePut(value = [SNAPSHOT_BACKFILL_CACHE], key = "#instrumentId")
  fun markBackfilled(instrumentId: Long): Boolean = true
}
