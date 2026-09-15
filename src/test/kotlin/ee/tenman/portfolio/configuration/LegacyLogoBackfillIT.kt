package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.asList
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.LEGACY_ETF_LOGOS_CACHE
import io.minio.GetObjectArgs
import io.minio.MinioClient
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.springframework.cache.CacheManager
import java.util.UUID

@IntegrationTest
class LegacyLogoBackfillIT {
  @Resource
  private lateinit var legacyLogoBackfill: LegacyLogoBackfill

  @Resource
  private lateinit var cacheManager: CacheManager

  @Resource
  private lateinit var minioClient: MinioClient

  @Resource
  private lateinit var minioProperties: MinioProperties

  @Test
  fun `should copy a logo cached only in the legacy cache into MinIO`() {
    val uuid = UUID.randomUUID()
    val logo = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x04)
    cacheManager.getCache(LEGACY_ETF_LOGOS_CACHE)?.putIfAbsent(uuid.toString(), logo)
    legacyLogoBackfill.backfill()
    val stored =
      minioClient
        .getObject(
          GetObjectArgs
          .builder()
          .bucket(minioProperties.bucketName)
          .`object`("logos/$uuid.png")
          .build(),
            ).use { it.readBytes() }
    expect(stored.asList()).toEqual(logo.asList())
  }

  @Test
  fun `should evict the legacy entry once the logo is copied`() {
    val uuid = UUID.randomUUID()
    cacheManager.getCache(LEGACY_ETF_LOGOS_CACHE)?.putIfAbsent(uuid.toString(), byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x05))
    legacyLogoBackfill.backfill()
    expect(cacheManager.getCache(LEGACY_ETF_LOGOS_CACHE)?.get(uuid.toString())).toEqual(null)
  }
}
