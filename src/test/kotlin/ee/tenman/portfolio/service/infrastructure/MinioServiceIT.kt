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
    val symbol = "AAPL"
    val testData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    minioService.uploadLogo(symbol, testData)

    val downloaded = minioService.downloadLogo(symbol)
    expect(downloaded).notToEqualNull()
    assertTrue(downloaded.contentEquals(testData))
  }

  @Test
  fun `should return null when logo does not exist`() {
    val downloaded = minioService.downloadLogo("NONEXISTENT")
    expect(downloaded).toEqual(null)
  }

  @Test
  fun `logoExists should return true when logo exists`() {
    val symbol = "TSLA"
    val testData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    minioService.uploadLogo(symbol, testData)

    val exists = minioService.logoExists(symbol)
    expect(exists).toEqual(true)
  }

  @Test
  fun `logoExists should return false when logo does not exist`() {
    val exists = minioService.logoExists("NONEXISTENT")
    expect(exists).toEqual(false)
  }

  @Test
  fun `should not upload logo if it already exists`() {
    val symbol = "MSFT"
    val originalData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x01)
    val newData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x02)

    minioService.uploadLogo(symbol, originalData)

    val existsBefore = minioService.logoExists(symbol)
    expect(existsBefore).toEqual(true)

    minioService.uploadLogo(symbol, newData)

    val downloaded = minioService.downloadLogo(symbol)
    expect(downloaded).notToEqualNull()
    assertTrue(downloaded.contentEquals(newData))
  }

  @Test
  fun `should handle symbol case insensitivity`() {
    val testData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    minioService.uploadLogo("googl", testData)

    val existsLowerCase = minioService.logoExists("googl")
    val existsUpperCase = minioService.logoExists("GOOGL")

    expect(existsLowerCase).toEqual(true)
    expect(existsUpperCase).toEqual(true)

    val downloadedLowerCase = minioService.downloadLogo("googl")
    val downloadedUpperCase = minioService.downloadLogo("GOOGL")

    expect(downloadedLowerCase).notToEqualNull()
    expect(downloadedUpperCase).notToEqualNull()
    assertTrue(downloadedLowerCase.contentEquals(testData))
    assertTrue(downloadedUpperCase.contentEquals(testData))
  }
}
