package ee.tenman.portfolio.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "minio")
data class MinioProperties(
  var endpoint: String = "http://localhost:9000",
  var accessKey: String = "minioadmin",
  var secretKey: String = "minioadmin",
  var bucketName: String = "portfolio-logos",
)
