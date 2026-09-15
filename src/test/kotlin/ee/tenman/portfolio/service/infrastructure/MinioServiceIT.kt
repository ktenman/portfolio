package ee.tenman.portfolio.service.infrastructure

import ch.tutteli.atrium.api.fluent.en_GB.asList
import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.configuration.MinioProperties
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ETF_LOGOS_CACHE
import io.minio.ListObjectsArgs
import io.minio.MinioClient
import io.minio.RemoveObjectArgs
import jakarta.annotation.Resource
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.cache.CacheManager
import java.time.Duration
import java.util.UUID

@IntegrationTest
class MinioServiceIT {
  @Resource
  private lateinit var minioService: MinioService

  @Resource
  private lateinit var minioClient: MinioClient

  @Resource
  private lateinit var minioProperties: MinioProperties

  @Resource
  private lateinit var cacheManager: CacheManager

  @AfterEach
  fun cleanup() {
    val objects =
      minioClient.listObjects(
        ListObjectsArgs
          .builder()
          .bucket(minioProperties.bucketName)
          .prefix("logos/")
          .build(),
      )

    objects.forEach { result ->
      val objectName = result.get().objectName()
      minioClient.removeObject(
        RemoveObjectArgs
          .builder()
          .bucket(minioProperties.bucketName)
          .`object`(objectName)
          .build(),
      )
    }
  }

  @Test
  fun `should upload and download logo successfully`() {
    val holdingUuid = UUID.randomUUID()
    val testData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    minioService.uploadLogo(holdingUuid, testData)

    val downloaded = minioService.downloadLogo(holdingUuid)
    expect(downloaded).notToEqualNull().asList().toEqual(testData.asList())
  }

  @Test
  fun `should return null when logo does not exist`() {
    val downloaded = minioService.downloadLogo(UUID.randomUUID())
    expect(downloaded).toEqual(null)
  }

  @Test
  fun `should throw instead of reporting a missing logo when the bucket is unreachable`() {
    val service = MinioService(minioClient, MinioProperties(bucketName = "missing-${UUID.randomUUID()}"))
    expect { service.downloadLogo(UUID.randomUUID()) }.toThrow<IllegalStateException>()
  }

  @Test
  fun `should overwrite logo if uploaded again`() {
    val holdingUuid = UUID.randomUUID()
    val originalData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x01)
    val newData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x02)

    minioService.uploadLogo(holdingUuid, originalData)
    minioService.uploadLogo(holdingUuid, newData)
    awaitCachedLogo(holdingUuid, newData)

    val downloaded = minioService.downloadLogo(holdingUuid)
    expect(downloaded).notToEqualNull().asList().toEqual(newData.asList())
  }

  @Test
  fun `should return uploaded logo when an earlier download missed`() {
    val holdingUuid = UUID.randomUUID()
    val testData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x03)
    minioService.downloadLogo(holdingUuid)
    minioService.uploadLogo(holdingUuid, testData)
    awaitCachedLogo(holdingUuid, testData)
    val downloaded = minioService.downloadLogo(holdingUuid)
    expect(downloaded).notToEqualNull().asList().toEqual(testData.asList())
  }

  private fun awaitCachedLogo(
    uuid: UUID,
    logo: ByteArray,
  ) {
    val cache = cacheManager.getCache(ETF_LOGOS_CACHE) ?: error("$ETF_LOGOS_CACHE not configured")
    await()
      .atMost(Duration.ofSeconds(2))
      .pollInterval(Duration.ofMillis(10))
      .until { (cache.get(uuid.toString())?.get() as? ByteArray)?.contentEquals(logo) == true }
  }
}
