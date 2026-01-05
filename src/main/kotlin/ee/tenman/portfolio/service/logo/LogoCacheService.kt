package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ETF_LOGOS_CACHE
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class LogoCacheService(
  private val cacheManager: CacheManager,
) {
  fun getLogo(uuid: UUID): ByteArray? =
    cacheManager.getCache(ETF_LOGOS_CACHE)?.get(uuid.toString())?.get() as? ByteArray

  fun saveLogo(
    uuid: UUID,
    logoData: ByteArray,
  ): ByteArray {
    cacheManager.getCache(ETF_LOGOS_CACHE)?.put(uuid.toString(), logoData)
    return logoData
  }

  fun logoExists(uuid: UUID): Boolean = cacheManager.getCache(ETF_LOGOS_CACHE)?.get(uuid.toString()) != null

  fun evictLogo(uuid: UUID) {
    cacheManager.getCache(ETF_LOGOS_CACHE)?.evict(uuid.toString())
  }
}
