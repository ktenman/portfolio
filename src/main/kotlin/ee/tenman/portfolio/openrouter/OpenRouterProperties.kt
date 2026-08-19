package ee.tenman.portfolio.openrouter

import ee.tenman.portfolio.domain.AiModel
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "openrouter")
data class OpenRouterProperties(
  val apiKey: String = "",
  val url: String = "https://openrouter.ai/api/v1",
  val primaryModel: AiModel = AiModel.primarySectorModel(),
  val fallbackModel: AiModel = AiModel.CLAUDE_SONNET_5,
  val visionModel: String = AiModel.GEMINI_3_5_FLASH_LITE.modelId,
  val circuitBreaker: CircuitBreakerProperties = CircuitBreakerProperties(),
  val apiTimeoutMs: Long = 30000,
)

data class CircuitBreakerProperties(
  val failureThreshold: Int = 3,
  val recoveryTimeoutSeconds: Long = 60,
) {
  init {
    require(failureThreshold > 0) { "failureThreshold must be positive" }
    require(recoveryTimeoutSeconds > 0) { "recoveryTimeoutSeconds must be positive" }
  }
}
