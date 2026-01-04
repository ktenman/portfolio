package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ETF_LOGOS_CACHE
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class LogoCacheService(
  private val cacheManager: CacheManager,
) {
  @Cacheable(value = [ETF_LOGOS_CACHE], key = "#uuid.toString()", unless = "#result == null")
  fun getLogo(uuid: UUID): ByteArray? = cacheManager.getCache(ETF_LOGOS_CACHE)?.get(uuid.toString())?.get() as? ByteArray

  @CachePut(value = [ETF_LOGOS_CACHE], key = "#uuid.toString()")
  fun saveLogo(
    uuid: UUID,
    logoData: ByteArray,
  ): ByteArray = logoData.also { require(uuid.toString().isNotBlank()) }

  fun logoExists(uuid: UUID): Boolean = cacheManager.getCache(ETF_LOGOS_CACHE)?.get(uuid.toString()) != null

  fun evictLogo(uuid: UUID) {
    cacheManager.getCache(ETF_LOGOS_CACHE)?.evict(uuid.toString())
  }
}
