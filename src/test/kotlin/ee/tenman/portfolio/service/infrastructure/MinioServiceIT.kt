package ee.tenman.portfolio.service.infrastructure

import ch.tutteli.atrium.api.fluent.en_GB.asList
import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.configuration.MinioProperties
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ETF_LOGOS_CACHE
import io.minio.BucketExistsArgs
import io.minio.ListObjectsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import io.minio.errors.ErrorResponseException
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
    evictCachedLogo(holdingUuid)

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
    val failure = runCatching { service.downloadLogo(UUID.randomUUID()) }.exceptionOrNull()
    expect(((failure as? IllegalStateException)?.cause as? ErrorResponseException)?.errorResponse()?.code()).toEqual("NoSuchBucket")
  }

  @Test
  fun `should overwrite logo if uploaded again`() {
    val holdingUuid = UUID.randomUUID()
    val originalData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x01)
    val newData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x02)

    minioService.uploadLogo(holdingUuid, originalData)
    minioService.uploadLogo(holdingUuid, newData)
    awaitCachedLogo(holdingUuid, newData)
    evictCachedLogo(holdingUuid)

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
    evictCachedLogo(holdingUuid)
    val downloaded = minioService.downloadLogo(holdingUuid)
    expect(downloaded).notToEqualNull().asList().toEqual(testData.asList())
  }

  @Test
  fun `should store uploaded logos with PNG content type`() {
    val uuid = UUID.randomUUID()
    minioService.uploadLogo(uuid, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
    val metadata =
      minioClient.statObject(
        StatObjectArgs
          .builder()
          .bucket(minioProperties.bucketName)
          .`object`("logos/$uuid.png")
          .build(),
      )
    expect(metadata.contentType()).toEqual("image/png")
  }

  @Test
  fun `should list uploaded logos`() {
    val uuid = UUID.randomUUID()
    val name = "logos/$uuid.png"
    minioService.uploadLogo(uuid, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
    val names =
      minioClient
        .listObjects(
          ListObjectsArgs
            .builder()
            .bucket(minioProperties.bucketName)
            .prefix(name)
            .build(),
        ).map { it.get().objectName() }
    expect(names).toEqual(listOf(name))
  }

  @Test
  fun `should return a missing logo after deleting its object`() {
    val uuid = UUID.randomUUID()
    val name = "logos/$uuid.png"
    minioService.uploadLogo(uuid, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
    minioClient.removeObject(
      RemoveObjectArgs
        .builder()
        .bucket(minioProperties.bucketName)
        .`object`(name)
        .build(),
    )
    evictCachedLogo(uuid)
    expect(minioService.downloadLogo(uuid)).toEqual(null)
  }

  @Test
  fun `should create and find a new bucket`() {
    val bucket = "logos-${UUID.randomUUID()}"
    val arguments = BucketExistsArgs.builder().bucket(bucket).build()
    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
    expect(minioClient.bucketExists(arguments)).toEqual(true)
  }

  @Test
  fun `should reject an invalid storage credential`() {
    val client =
      MinioClient
        .builder()
        .endpoint(minioProperties.endpoint)
        .credentials("invalid", "invalid")
        .build()
    val service = MinioService(client, minioProperties)
    val failure = runCatching { service.downloadLogo(UUID.randomUUID()) }.exceptionOrNull()
    expect(((failure as? IllegalStateException)?.cause as? ErrorResponseException)?.errorResponse()?.code()).toEqual("InvalidAccessKeyId")
  }

  private fun evictCachedLogo(uuid: UUID) {
    val cache = cacheManager.getCache(ETF_LOGOS_CACHE) ?: error("$ETF_LOGOS_CACHE not configured")
    cache.evict(uuid.toString())
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
