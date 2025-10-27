package ee.tenman.portfolio.configuration

import io.minio.MinioClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MinioConfiguration(
  private val minioProperties: MinioProperties,
) {
  @Bean
  fun minioClient(): MinioClient =
    MinioClient
      .builder()
      .endpoint(minioProperties.endpoint)
      .credentials(minioProperties.accessKey, minioProperties.secretKey)
      .build()
}
