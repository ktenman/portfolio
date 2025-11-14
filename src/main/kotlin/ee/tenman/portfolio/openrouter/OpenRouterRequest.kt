package ee.tenman.portfolio.openrouter

data class OpenRouterRequest(
  val model: String,
  val messages: List<Message>,
  val maxTokens: Int = 50,
  val temperature: Double = 0.1,
) {
  data class Message(
    val role: String,
    val content: String,
  )
}
