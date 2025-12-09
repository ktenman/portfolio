package ee.tenman.portfolio.domain

enum class AiModel(
  val modelId: String,
  val rateLimitPerMinute: Int,
) {
  CLAUDE_3_HAIKU("anthropic/claude-3-haiku", 30),
  CLAUDE_HAIKU_4_5("anthropic/claude-haiku-4.5", 7),
  ;

  companion object {
    fun fromModelId(modelId: String): AiModel? = entries.find { it.modelId.equals(modelId, ignoreCase = true) }
  }
}
