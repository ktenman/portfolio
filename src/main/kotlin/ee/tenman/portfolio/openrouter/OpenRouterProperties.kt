package ee.tenman.portfolio.openrouter

import ee.tenman.portfolio.domain.AiModel
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "openrouter")
data class OpenRouterProperties(
  val apiKey: String = "",
  val url: String = "https://openrouter.ai/api/v1",
  val primaryModel: AiModel = AiModel.DEEPSEEK_V4_FLASH,
  val fallbackModel: AiModel = AiModel.CLAUDE_SONNET_4_6,
  val visionModel: String = "google/gemini-3-flash-preview",
  val circuitBreaker: CircuitBreakerProperties = CircuitBreakerProperties(),
  val apiTimeoutMs: Long = 30000,
)
