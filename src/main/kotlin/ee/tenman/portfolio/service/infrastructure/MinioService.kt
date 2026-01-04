package ee.tenman.portfolio.service.infrastructure

import ee.tenman.portfolio.configuration.MinioProperties
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ETF_LOGOS_CACHE
import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.StatObjectArgs
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID

@Service
class MinioService(
  private val minioClient: MinioClient,
  private val minioProperties: MinioProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun logoExists(uuid: UUID): Boolean = objectExists("logos/$uuid.png")

  fun uploadLogo(
    uuid: UUID,
    logoData: ByteArray,
    contentType: String = "image/png",
  ) = uploadObject("logos/$uuid.png", logoData, contentType)

  @Cacheable(value = [ETF_LOGOS_CACHE], key = "'uuid-' + #uuid.toString()")
  fun downloadLogo(uuid: UUID): ByteArray? = downloadObject("logos/$uuid.png")

  private fun objectExists(objectName: String): Boolean =
    try {
      minioClient.statObject(
        StatObjectArgs
          .builder()
          .bucket(minioProperties.bucketName)
          .`object`(objectName)
          .build(),
      )
      true
    } catch (e: Exception) {
      log.trace("Object not found: $objectName, reason: ${e.message}")
      false
    }

  private fun uploadObject(
    objectName: String,
    data: ByteArray,
    contentType: String,
  ) {
    minioClient.putObject(
      PutObjectArgs
        .builder()
        .bucket(minioProperties.bucketName)
        .`object`(objectName)
        .stream(ByteArrayInputStream(data), data.size.toLong(), -1)
        .contentType(contentType)
        .build(),
    )
    log.debug("Uploaded object: $objectName")
  }

  private fun downloadObject(objectName: String): ByteArray? =
    try {
      minioClient
        .getObject(
          GetObjectArgs
            .builder()
            .bucket(minioProperties.bucketName)
            .`object`(objectName)
            .build(),
        ).use { stream: InputStream ->
          stream.readBytes()
        }
    } catch (e: Exception) {
      log.trace("Object not found: $objectName, reason: ${e.message}")
      null
    }
}
