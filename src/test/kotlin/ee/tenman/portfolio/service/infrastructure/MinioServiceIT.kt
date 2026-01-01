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
import java.util.UUID

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
    val holdingUuid = UUID.randomUUID()
    val testData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    minioService.uploadLogo(holdingUuid, testData)

    val downloaded = minioService.downloadLogo(holdingUuid)
    expect(downloaded).notToEqualNull()
    assertTrue(downloaded.contentEquals(testData))
  }

  @Test
  fun `should return null when logo does not exist`() {
    val downloaded = minioService.downloadLogo(UUID.randomUUID())
    expect(downloaded).toEqual(null)
  }

  @Test
  fun `logoExists should return true when logo exists`() {
    val holdingUuid = UUID.randomUUID()
    val testData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    minioService.uploadLogo(holdingUuid, testData)

    val exists = minioService.logoExists(holdingUuid)
    expect(exists).toEqual(true)
  }

  @Test
  fun `logoExists should return false when logo does not exist`() {
    val exists = minioService.logoExists(UUID.randomUUID())
    expect(exists).toEqual(false)
  }

  @Test
  fun `should overwrite logo if uploaded again`() {
    val holdingUuid = UUID.randomUUID()
    val originalData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x01)
    val newData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x02)

    minioService.uploadLogo(holdingUuid, originalData)

    val existsBefore = minioService.logoExists(holdingUuid)
    expect(existsBefore).toEqual(true)

    minioService.uploadLogo(holdingUuid, newData)

    val downloaded = minioService.downloadLogo(holdingUuid)
    expect(downloaded).notToEqualNull()
    assertTrue(downloaded.contentEquals(newData))
  }
}
