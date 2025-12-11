package ee.tenman.portfolio.domain

enum class AiModel(
  val modelId: String,
  val rateLimitPerMinute: Int,
  val fallbackTier: Int,
) {
  GEMINI_2_5_FLASH("google/gemini-2.5-flash", 100, 0),
  GROK_4_1_FAST("x-ai/grok-4.1-fast", 60, 1),
  CLAUDE_3_HAIKU("anthropic/claude-3-haiku", 30, 2),
  CLAUDE_HAIKU_4_5("anthropic/claude-haiku-4.5", 7, 3),
  GEMINI_3_PRO_PREVIEW("google/gemini-3-pro-preview", 2, 4),
  GROK_4("x-ai/grok-4", 2, 5),
  CLAUDE_SONNET_4_5("anthropic/claude-sonnet-4.5", 2, 6),
  CLAUDE_OPUS_4_5("anthropic/claude-opus-4.5", 1, 7),
  ;

  fun nextFallbackModel(): AiModel? = entries.find { it.fallbackTier == this.fallbackTier + 1 }

  companion object {
    fun fromModelId(modelId: String): AiModel? = entries.find { it.modelId.equals(modelId, ignoreCase = true) }
  }
}
