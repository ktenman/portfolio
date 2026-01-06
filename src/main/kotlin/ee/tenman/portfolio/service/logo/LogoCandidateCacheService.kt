package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.LOGO_CANDIDATES_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.LOGO_NAME_SEARCH_CACHE
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

  fun getCachedDataByName(name: String): CachedLogoData? =
    cacheManager.getCache(LOGO_NAME_SEARCH_CACHE)?.get(normalizeKey(name))?.get() as? CachedLogoData

  fun cacheByName(
    name: String,
    candidates: List<LogoCandidate>,
    imageData: Map<Int, ByteArray>,
  ): CachedLogoData {
    val data = CachedLogoData(candidates = candidates, images = imageData)
    cacheManager.getCache(LOGO_NAME_SEARCH_CACHE)?.put(normalizeKey(name), data)
    return data
  }

  private fun normalizeKey(name: String): String = name.lowercase().trim()
}
