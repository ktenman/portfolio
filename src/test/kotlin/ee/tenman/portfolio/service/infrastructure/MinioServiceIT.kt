package ee.tenman.portfolio.service.infrastructure

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.configuration.MinioProperties
import io.minio.ListObjectsArgs
import io.minio.MinioClient
import io.minio.RemoveObjectArgs
import jakarta.annotation.Resource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@IntegrationTest
class MinioServiceIT {
  @Resource
  private lateinit var minioService: MinioService

  @Resource
  private lateinit var minioClient: MinioClient

  @Resource
  private lateinit var minioProperties: MinioProperties

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
    val holdingId = 12345L
    val testData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    minioService.uploadLogo(holdingId, testData)

    val downloaded = minioService.downloadLogo(holdingId)
    expect(downloaded).notToEqualNull()
    assertTrue(downloaded.contentEquals(testData))
  }

  @Test
  fun `should return null when logo does not exist`() {
    val downloaded = minioService.downloadLogo(99999L)
    expect(downloaded).toEqual(null)
  }

  @Test
  fun `logoExists should return true when logo exists`() {
    val holdingId = 67890L
    val testData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    minioService.uploadLogo(holdingId, testData)

    val exists = minioService.logoExists(holdingId)
    expect(exists).toEqual(true)
  }

  @Test
  fun `logoExists should return false when logo does not exist`() {
    val exists = minioService.logoExists(88888L)
    expect(exists).toEqual(false)
  }

  @Test
  fun `should overwrite logo if uploaded again`() {
    val holdingId = 11111L
    val originalData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x01)
    val newData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x02)

    minioService.uploadLogo(holdingId, originalData)

    val existsBefore = minioService.logoExists(holdingId)
    expect(existsBefore).toEqual(true)

    minioService.uploadLogo(holdingId, newData)

    val downloaded = minioService.downloadLogo(holdingId)
    expect(downloaded).notToEqualNull()
    assertTrue(downloaded.contentEquals(newData))
  }
}
