package ee.tenman.portfolio.service.infrastructure

import ee.tenman.portfolio.configuration.MinioProperties
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ETF_LOGOS_CACHE
import io.minio.GetObjectArgs
import io.minio.ListObjectsArgs
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
import java.time.LocalDate
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
    uploadObject("logos/$uuid.png", logoData, MediaType.IMAGE_PNG_VALUE)
    return logoData
  }

  @Cacheable(value = [ETF_LOGOS_CACHE], key = "#uuid.toString()")
  fun downloadLogo(uuid: UUID): ByteArray? = downloadObject("logos/$uuid.png")

  fun uploadFundReport(
    isin: String,
    asOfDate: LocalDate,
    pdf: ByteArray,
  ) = uploadObject(reportName(isin, asOfDate), pdf, MediaType.APPLICATION_PDF_VALUE)

  fun downloadFundReport(
    isin: String,
    asOfDate: LocalDate,
  ): ByteArray? = downloadObject(reportName(isin, asOfDate))

  fun fundReportDates(isin: String): Set<LocalDate> =
    minioClient
      .listObjects(
        ListObjectsArgs
          .builder()
          .bucket(minioProperties.bucketName)
          .prefix("$REPORTS/$isin/")
          .build(),
      ).map { reportDate(it.get().objectName()) }
      .toSet()

  private fun reportName(
    isin: String,
    asOfDate: LocalDate,
  ) = "$REPORTS/$isin/$asOfDate.pdf"

  private fun reportDate(objectName: String): LocalDate = LocalDate.parse(objectName.substringAfterLast('/').removeSuffix(".pdf"))

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
    private const val REPORTS = "fund-reports"
  }
}
