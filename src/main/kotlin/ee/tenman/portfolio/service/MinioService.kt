package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.MinioProperties
import ee.tenman.portfolio.util.LogSanitizerUtil
import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.InputStream

@Service
class MinioService(
  private val minioClient: MinioClient,
  private val minioProperties: MinioProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun logoExists(symbol: String): Boolean =
    try {
      val objectName = "logos/${symbol.uppercase()}.png"
      minioClient
        .statObject(
          io.minio.StatObjectArgs
            .builder()
            .bucket(minioProperties.bucketName)
            .`object`(objectName)
            .build(),
        )
      true
    } catch (e: Exception) {
      log.trace("Logo not found for {}: {}", symbol, e.message)
      false
    }

  fun uploadLogo(
    symbol: String,
    logoData: ByteArray,
    contentType: String = "image/png",
  ) {
    val objectName = "logos/${symbol.uppercase()}.png"
    minioClient.putObject(
      PutObjectArgs
        .builder()
        .bucket(minioProperties.bucketName)
        .`object`(objectName)
        .stream(ByteArrayInputStream(logoData), logoData.size.toLong(), -1)
        .contentType(contentType)
        .build(),
    )
    log.debug("Uploaded logo for symbol: {}", symbol)
  }

  @Cacheable(value = ["etfLogos"], key = "#symbol")
  fun downloadLogo(symbol: String): ByteArray? =
    try {
    val objectName = "logos/${symbol.uppercase()}.png"
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
    log.warn("Failed to download logo for symbol: {}", LogSanitizerUtil.sanitize(symbol), e)
    null
  }
}
