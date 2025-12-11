package ee.tenman.portfolio.openrouter

import ee.tenman.portfolio.domain.AiModel
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "openrouter")
data class OpenRouterProperties(
  val apiKey: String = "",
  val url: String = "https://openrouter.ai/api/v1",
  val primaryModel: AiModel = AiModel.GEMINI_2_5_FLASH,
  val fallbackModel: AiModel = AiModel.GROK_4_1_FAST,
  val circuitBreaker: CircuitBreakerProperties = CircuitBreakerProperties(),
)
