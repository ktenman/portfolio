package ee.tenman.portfolio.openrouter

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenRouterResponse(
  val choices: List<Choice>? = null,
) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  data class Choice(
    val message: Message? = null,
  )

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class Message(
    val content: String? = null,
    val reasoning: String? = null,
  )

  fun extractContent(): String? {
    val message = choices?.firstOrNull()?.message
    return message?.content?.trim()?.takeIf { it.isNotBlank() }
      ?: message?.reasoning?.trim()?.takeIf { it.isNotBlank() }
  }
}
