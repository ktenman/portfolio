package ee.tenman.portfolio.openrouter

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "openrouter")
data class OpenRouterProperties(
  val apiKey: String = "",
  val url: String = "https://openrouter.ai/api/v1",
  val circuitBreaker: CircuitBreakerProperties = CircuitBreakerProperties(),
)
