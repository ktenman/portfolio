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

  fun extractContent(): String? =
    choices
      ?.firstOrNull()
      ?.message
      ?.content
      ?.trim()
      ?.takeIf { it.isNotBlank() }
}
