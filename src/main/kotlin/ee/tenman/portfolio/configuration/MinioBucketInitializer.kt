package ee.tenman.portfolio.configuration

import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class MinioBucketInitializer(
  private val minioClient: MinioClient,
  private val minioProperties: MinioProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @EventListener(ApplicationReadyEvent::class)
  fun initializeBucket() {
    try {
      val bucketExists =
        minioClient.bucketExists(
          BucketExistsArgs
            .builder()
            .bucket(minioProperties.bucketName)
            .build(),
        )

      if (!bucketExists) {
        minioClient.makeBucket(
          MakeBucketArgs
            .builder()
            .bucket(minioProperties.bucketName)
            .build(),
        )
        log.info("Created MinIO bucket: ${minioProperties.bucketName}")
      } else {
        log.info("MinIO bucket already exists: ${minioProperties.bucketName}")
      }
    } catch (e: Exception) {
      log.warn(
        "Failed to initialize MinIO bucket '${minioProperties.bucketName}'. " +
          "MinIO may not be running. Logo upload/download features will not work until MinIO is available",
        e,
      )
    }
  }
}
