package ee.tenman.portfolio.openrouter

import ee.tenman.portfolio.domain.AiModel
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "openrouter")
data class OpenRouterProperties(
  val apiKey: String = "",
  val url: String = "https://openrouter.ai/api/v1",
  val primaryModel: AiModel = AiModel.GEMINI_3_FLASH_PREVIEW,
  val fallbackModel: AiModel = AiModel.CLAUDE_OPUS_4_5,
  val circuitBreaker: CircuitBreakerProperties = CircuitBreakerProperties(),
)
