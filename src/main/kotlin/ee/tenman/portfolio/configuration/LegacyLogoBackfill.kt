package ee.tenman.portfolio.configuration

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.LEGACY_ETF_LOGOS_CACHE
import ee.tenman.portfolio.service.infrastructure.MinioService
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class LegacyLogoBackfill(
  private val cacheManager: CacheManager,
  private val redisTemplate: StringRedisTemplate,
  private val minioService: MinioService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Order(Ordered.LOWEST_PRECEDENCE)
  @EventListener(ApplicationReadyEvent::class)
  fun backfill() {
    runCatching { copyAll() }
      .onFailure { log.warn("Failed to backfill legacy logos into MinIO", it) }
  }

  private fun copyAll() {
    val cache = cacheManager.getCache(LEGACY_ETF_LOGOS_CACHE) ?: return
    val keys = redisTemplate.keys("$LEGACY_ETF_LOGOS_CACHE::*").map { it.substringAfter("::") }
    if (keys.isEmpty()) return
    val copied = keys.count { copy(cache, it) }
    log.info("Copied $copied of ${keys.size} legacy logos into MinIO")
  }

  private fun copy(
    cache: Cache,
    key: String,
  ): Boolean =
    runCatching {
      val logo = cache.get(key)?.get() as? ByteArray ?: return false
      minioService.uploadLogo(UUID.fromString(key), logo)
      cache.evictIfPresent(key)
    }.onFailure { log.warn("Failed to copy legacy logo $key into MinIO", it) }
      .isSuccess
}
