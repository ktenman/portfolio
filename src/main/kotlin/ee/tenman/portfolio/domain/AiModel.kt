package ee.tenman.portfolio.domain

enum class AiModel(
  val modelId: String,
  val rateLimitPerMinute: Int,
  val sectorFallbackTier: Int = -1,
  val countryFallbackTier: Int = -1,
) {
  DEEPSEEK_V4_FLASH("deepseek/deepseek-v4-flash", 240, sectorFallbackTier = 0, countryFallbackTier = 0),
  GEMINI_3_FLASH_PREVIEW("google/gemini-3-flash-preview", 400, sectorFallbackTier = 1, countryFallbackTier = 2),
  CLAUDE_SONNET_5("anthropic/claude-sonnet-5", 240, sectorFallbackTier = 2, countryFallbackTier = 1),
  DEEPSEEK_V4_PRO("deepseek/deepseek-v4-pro", 240, sectorFallbackTier = 3, countryFallbackTier = 3),
  GPT_5_5("openai/gpt-5.5", 240, sectorFallbackTier = 4, countryFallbackTier = 4),
  CLAUDE_OPUS_4_8("anthropic/claude-opus-4.8", 240, sectorFallbackTier = 5, countryFallbackTier = 5),
  GPT_5_4_NANO("openai/gpt-5.4-nano", 240),
  CLAUDE_SONNET_4_6("anthropic/claude-sonnet-4.6", 240),
  DEEPSEEK_V3_2("deepseek/deepseek-v3.2", 240),
  GPT_5_4("openai/gpt-5.4", 240),
  CLAUDE_OPUS_4_6("anthropic/claude-opus-4.6", 240),
  ;

  fun nextSectorFallbackModel(): AiModel? = entries.find { it.sectorFallbackTier == this.sectorFallbackTier + 1 }

  fun nextCountryFallbackModel(): AiModel? = entries.find { it.countryFallbackTier == this.countryFallbackTier + 1 }

  companion object {
    fun fromModelId(modelId: String): AiModel? = entries.find { it.modelId.equals(modelId, ignoreCase = true) }

    fun primarySectorModel(): AiModel = entries.first { it.sectorFallbackTier == 0 }

    fun primaryCountryModel(): AiModel = entries.first { it.countryFallbackTier == 0 }
  }
}
