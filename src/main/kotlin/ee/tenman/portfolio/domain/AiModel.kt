package ee.tenman.portfolio.domain

enum class AiModel(
  val modelId: String,
  val rateLimitPerMinute: Int,
  val fallbackTier: Int,
) {
  CLAUDE_3_HAIKU("anthropic/claude-3-haiku", 30, 0),
  CLAUDE_HAIKU_4_5("anthropic/claude-haiku-4.5", 7, 1),
  CLAUDE_SONNET_4_5("anthropic/claude-sonnet-4.5", 2, 2),
  CLAUDE_OPUS_4_5("anthropic/claude-opus-4.5", 1, 3),
  ;

  fun getNextFallback(): AiModel? = entries.find { it.fallbackTier == this.fallbackTier + 1 }

  companion object {
    fun fromModelId(modelId: String): AiModel? = entries.find { it.modelId.equals(modelId, ignoreCase = true) }
  }
}
