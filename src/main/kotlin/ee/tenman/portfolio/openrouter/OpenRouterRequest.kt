package ee.tenman.portfolio.openrouter

import com.fasterxml.jackson.annotation.JsonProperty

data class OpenRouterRequest(
  val model: String,
  val messages: List<Message>,
  @JsonProperty("max_tokens")
  val maxTokens: Int = 50,
  val temperature: Double = 0.1,
) {
  data class Message(
    val role: String,
    val content: String,
  )
}
