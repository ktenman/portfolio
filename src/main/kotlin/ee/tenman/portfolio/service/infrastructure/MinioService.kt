package ee.tenman.portfolio.service.infrastructure

import ee.tenman.portfolio.configuration.MinioProperties
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ETF_LOGOS_CACHE
import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.errors.ErrorResponseException
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.MediaType
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

  @CachePut(value = [ETF_LOGOS_CACHE], key = "#uuid.toString()")
  fun uploadLogo(
    uuid: UUID,
    logoData: ByteArray,
  ): ByteArray {
    uploadObject("logos/$uuid.png", logoData)
    return logoData
  }

  @Cacheable(value = [ETF_LOGOS_CACHE], key = "#uuid.toString()")
  fun downloadLogo(uuid: UUID): ByteArray? = downloadObject("logos/$uuid.png")

  private fun uploadObject(
    objectName: String,
    data: ByteArray,
  ) {
    minioClient.putObject(
      PutObjectArgs
        .builder()
        .bucket(minioProperties.bucketName)
        .`object`(objectName)
        .stream(ByteArrayInputStream(data), data.size.toLong(), -1)
        .contentType(MediaType.IMAGE_PNG_VALUE)
        .build(),
    )
    log.debug("Uploaded object: $objectName")
  }

  private fun downloadObject(objectName: String): ByteArray? =
    runCatching { readObject(objectName) }
      .getOrElse {
        if (it.isMissingObject()) return null
        throw IllegalStateException("Failed to download MinIO object $objectName from bucket ${minioProperties.bucketName}", it)
      }

  private fun readObject(objectName: String): ByteArray =
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

  private fun Throwable.isMissingObject(): Boolean = this is ErrorResponseException && errorResponse().code() == NO_SUCH_KEY

  companion object {
    private const val NO_SUCH_KEY = "NoSuchKey"
  }
}
