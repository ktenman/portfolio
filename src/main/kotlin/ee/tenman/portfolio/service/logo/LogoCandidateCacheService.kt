package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.LOGO_CANDIDATES_CACHE
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class LogoCandidateCacheService(
  private val cacheManager: CacheManager,
) {
  fun getCachedData(holdingUuid: UUID): CachedLogoData? =
    cacheManager.getCache(LOGO_CANDIDATES_CACHE)?.get(holdingUuid.toString())?.get() as? CachedLogoData

  fun cacheValidatedCandidates(
    holdingUuid: UUID,
    candidates: List<LogoCandidate>,
    imageData: Map<Int, ByteArray>,
  ): CachedLogoData {
    val data = CachedLogoData(candidates = candidates, images = imageData)
    cacheManager.getCache(LOGO_CANDIDATES_CACHE)?.put(holdingUuid.toString(), data)
    return data
  }

  fun clearCache(holdingUuid: UUID) {
    cacheManager.getCache(LOGO_CANDIDATES_CACHE)?.evict(holdingUuid.toString())
  }
}
