package ee.tenman.portfolio.configuration

import ee.tenman.portfolio.domain.AiModel
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "batch-logo-validation")
data class BatchLogoValidationProperties(
  val enabled: Boolean = true,
  val model: AiModel = AiModel.GEMINI_3_FLASH_PREVIEW,
  val batchSize: Int = 25,
  val imagesPerCompany: Int = 10,
  val maxTokens: Int = 2000,
  val temperature: Double = 0.0,
  val downloadTimeoutMs: Long = 5000,
  val apiTimeoutMs: Long = 60000,
)
