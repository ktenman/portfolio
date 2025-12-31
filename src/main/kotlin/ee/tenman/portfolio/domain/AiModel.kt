package ee.tenman.portfolio.domain

enum class AiModel(
  val modelId: String,
  val rateLimitPerMinute: Int,
  val sectorFallbackTier: Int = -1,
  val countryFallbackTier: Int = -1,
) {
  GEMINI_3_FLASH_PREVIEW("google/gemini-3-flash-preview", 400, sectorFallbackTier = 0, countryFallbackTier = 1),
  CLAUDE_OPUS_4_5("anthropic/claude-opus-4.5", 240, sectorFallbackTier = 1, countryFallbackTier = 0),
  CLAUDE_SONNET_4_5("anthropic/claude-sonnet-4.5", 240, sectorFallbackTier = 2),
  GEMINI_2_5_FLASH("google/gemini-2.5-flash", 400, sectorFallbackTier = 3),
  DEEPSEEK_V3_2("deepseek/deepseek-v3.2", 240, sectorFallbackTier = 4, countryFallbackTier = 2),
  ;

  fun nextSectorFallbackModel(): AiModel? = entries.find { it.sectorFallbackTier == this.sectorFallbackTier + 1 }

  fun nextCountryFallbackModel(): AiModel? = entries.find { it.countryFallbackTier == this.countryFallbackTier + 1 }

  companion object {
    fun fromModelId(modelId: String): AiModel? = entries.find { it.modelId.equals(modelId, ignoreCase = true) }

    fun primarySectorModel(): AiModel = GEMINI_3_FLASH_PREVIEW

    fun primaryCountryModel(): AiModel = CLAUDE_OPUS_4_5
  }
}
