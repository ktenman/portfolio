package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.LOGO_CANDIDATES_CACHE
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class LogoCandidateCacheService(
  private val cacheManager: CacheManager,
) {
  @Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
  @Cacheable(value = [LOGO_CANDIDATES_CACHE], key = "#holdingUuid.toString()", unless = "#result == null")
  fun getCachedData(holdingUuid: UUID): CachedLogoData? = null

  @Suppress("UnusedParameter")
  @CachePut(value = [LOGO_CANDIDATES_CACHE], key = "#holdingUuid.toString()")
  fun cacheValidatedCandidates(
    holdingUuid: UUID,
    candidates: List<LogoCandidate>,
    imageData: Map<Int, ByteArray>,
  ): CachedLogoData = CachedLogoData(candidates = candidates, images = imageData)

  fun clearCache(holdingUuid: UUID) {
    cacheManager.getCache(LOGO_CANDIDATES_CACHE)?.evict(holdingUuid.toString())
  }
}
