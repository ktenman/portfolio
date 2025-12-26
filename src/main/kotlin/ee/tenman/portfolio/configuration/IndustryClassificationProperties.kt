package ee.tenman.portfolio.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "industry-classification")
data class IndustryClassificationProperties(
  val enabled: Boolean = true,
  val rateLimitBufferMs: Long = 100L,
)
