package ee.tenman.portfolio.domain

enum class AiModel(
  val modelId: String,
  val rateLimitPerMinute: Int,
  val sectorFallbackTier: Int = -1,
  val countryFallbackTier: Int = -1,
) {
  GEMINI_4_ARGON("google/gemini-4-argon", 400, sectorFallbackTier = 0, countryFallbackTier = 0),
  CLAUDE_OPUS_5_5("anthropic/claude-opus-5.5", 240, sectorFallbackTier = 1, countryFallbackTier = 1),
  GPT_6_LUNA("openai/gpt-6-luna", 240, sectorFallbackTier = 2, countryFallbackTier = 2),
  DEEPSEEK_V4_PRO("deepseek/deepseek-v4-pro", 240, sectorFallbackTier = 3, countryFallbackTier = 3),
  ;

  fun nextSectorFallbackModel(): AiModel? = entries.find { it.sectorFallbackTier == this.sectorFallbackTier + 1 }

  fun nextCountryFallbackModel(): AiModel? = entries.find { it.countryFallbackTier == this.countryFallbackTier + 1 }

  companion object {
    fun fromModelId(modelId: String): AiModel? = entries.find { it.modelId.equals(modelId, ignoreCase = true) }

    fun primarySectorModel(): AiModel = entries.first { it.sectorFallbackTier == 0 }

    fun primaryCountryModel(): AiModel = entries.first { it.countryFallbackTier == 0 }
  }
}
