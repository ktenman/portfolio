package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.AiModel
import org.junit.jupiter.api.Test

class AiModelTest {
  @Test
  fun `should return GEMINI_3_5_FLASH_LITE for matching model id`() {
    val result = AiModel.fromModelId("google/gemini-3.5-flash-lite")

    expect(result).toEqual(AiModel.GEMINI_3_5_FLASH_LITE)
  }

  @Test
  fun `should return CLAUDE_SONNET_5 for matching model id`() {
    val result = AiModel.fromModelId("anthropic/claude-sonnet-5")

    expect(result).toEqual(AiModel.CLAUDE_SONNET_5)
  }

  @Test
  fun `should return CLAUDE_OPUS_5 for matching model id`() {
    val result = AiModel.fromModelId("anthropic/claude-opus-5")

    expect(result).toEqual(AiModel.CLAUDE_OPUS_5)
  }

  @Test
  fun `should return DEEPSEEK_V4_PRO for matching model id`() {
    val result = AiModel.fromModelId("deepseek/deepseek-v4-pro")

    expect(result).toEqual(AiModel.DEEPSEEK_V4_PRO)
  }

  @Test
  fun `should return DEEPSEEK_V4_FLASH for matching model id`() {
    val result = AiModel.fromModelId("deepseek/deepseek-v4-flash")

    expect(result).toEqual(AiModel.DEEPSEEK_V4_FLASH)
  }

  @Test
  fun `should return GPT_5_6_TERRA for matching model id`() {
    val result = AiModel.fromModelId("openai/gpt-5.6-terra")

    expect(result).toEqual(AiModel.GPT_5_6_TERRA)
  }

  @Test
  fun `should return GPT_5_6_LUNA for matching model id`() {
    val result = AiModel.fromModelId("openai/gpt-5.6-luna")

    expect(result).toEqual(AiModel.GPT_5_6_LUNA)
    expect(AiModel.GPT_5_6_LUNA.modelId).toEqual("openai/gpt-5.6-luna")
  }

  @Test
  fun `should return null for unknown model id`() {
    val result = AiModel.fromModelId("unknown/model")

    expect(result).toEqual(null)
  }

  @Test
  fun `should match model id case insensitively`() {
    val result = AiModel.fromModelId("GOOGLE/GEMINI-3.5-FLASH-LITE")

    expect(result).toEqual(AiModel.GEMINI_3_5_FLASH_LITE)
  }

  @Test
  fun `should keep retired model names resolvable for stored classifications`() {
    expect(RETIRED.map { AiModel.valueOf(it).sectorFallbackTier }.toSet()).toEqual(setOf(-1))
  }

  @Test
  fun `should keep retired model names out of country cascade`() {
    expect(RETIRED.map { AiModel.valueOf(it).countryFallbackTier }.toSet()).toEqual(setOf(-1))
  }

  @Test
  fun `should have correct rate limits for GEMINI_3_5_FLASH_LITE`() {
    expect(AiModel.GEMINI_3_5_FLASH_LITE.rateLimitPerMinute).toEqual(400)
  }

  @Test
  fun `should have correct rate limits for CLAUDE_SONNET_5`() {
    expect(AiModel.CLAUDE_SONNET_5.rateLimitPerMinute).toEqual(240)
  }

  @Test
  fun `should have correct rate limits for CLAUDE_OPUS_5`() {
    expect(AiModel.CLAUDE_OPUS_5.rateLimitPerMinute).toEqual(240)
  }

  @Test
  fun `should have correct rate limits for DEEPSEEK_V4_PRO`() {
    expect(AiModel.DEEPSEEK_V4_PRO.rateLimitPerMinute).toEqual(240)
  }

  @Test
  fun `should have correct rate limits for GPT_5_6_TERRA`() {
    expect(AiModel.GPT_5_6_TERRA.rateLimitPerMinute).toEqual(240)
  }

  @Test
  fun `should have correct rate limits for DEEPSEEK_V4_FLASH`() {
    expect(AiModel.DEEPSEEK_V4_FLASH.rateLimitPerMinute).toEqual(240)
  }

  @Test
  fun `should have correct sector fallback tiers`() {
    expect(AiModel.DEEPSEEK_V4_FLASH.sectorFallbackTier).toEqual(0)
    expect(AiModel.GEMINI_3_5_FLASH_LITE.sectorFallbackTier).toEqual(1)
    expect(AiModel.CLAUDE_SONNET_5.sectorFallbackTier).toEqual(2)
    expect(AiModel.DEEPSEEK_V4_PRO.sectorFallbackTier).toEqual(3)
    expect(AiModel.GPT_5_6_TERRA.sectorFallbackTier).toEqual(4)
    expect(AiModel.CLAUDE_OPUS_5.sectorFallbackTier).toEqual(5)
  }

  @Test
  fun `should return next sector fallback model for DEEPSEEK_V4_FLASH`() {
    expect(AiModel.DEEPSEEK_V4_FLASH.nextSectorFallbackModel()).toEqual(AiModel.GEMINI_3_5_FLASH_LITE)
  }

  @Test
  fun `should return next sector fallback model for GEMINI_3_5_FLASH_LITE`() {
    expect(AiModel.GEMINI_3_5_FLASH_LITE.nextSectorFallbackModel()).toEqual(AiModel.CLAUDE_SONNET_5)
  }

  @Test
  fun `should return next sector fallback model for CLAUDE_SONNET_5`() {
    expect(AiModel.CLAUDE_SONNET_5.nextSectorFallbackModel()).toEqual(AiModel.DEEPSEEK_V4_PRO)
  }

  @Test
  fun `should return next sector fallback model for DEEPSEEK_V4_PRO`() {
    expect(AiModel.DEEPSEEK_V4_PRO.nextSectorFallbackModel()).toEqual(AiModel.GPT_5_6_TERRA)
  }

  @Test
  fun `should return next sector fallback model for GPT_5_6_TERRA`() {
    expect(AiModel.GPT_5_6_TERRA.nextSectorFallbackModel()).toEqual(AiModel.CLAUDE_OPUS_5)
  }

  @Test
  fun `should return null for CLAUDE_OPUS_5 as last sector fallback`() {
    expect(AiModel.CLAUDE_OPUS_5.nextSectorFallbackModel()).toEqual(null)
  }

  @Test
  fun `should have correct country fallback tiers`() {
    expect(AiModel.DEEPSEEK_V4_FLASH.countryFallbackTier).toEqual(0)
    expect(AiModel.CLAUDE_SONNET_5.countryFallbackTier).toEqual(1)
    expect(AiModel.GEMINI_3_5_FLASH_LITE.countryFallbackTier).toEqual(2)
    expect(AiModel.DEEPSEEK_V4_PRO.countryFallbackTier).toEqual(3)
    expect(AiModel.GPT_5_6_TERRA.countryFallbackTier).toEqual(4)
    expect(AiModel.CLAUDE_OPUS_5.countryFallbackTier).toEqual(5)
  }

  @Test
  fun `should return next country fallback model for DEEPSEEK_V4_FLASH`() {
    expect(AiModel.DEEPSEEK_V4_FLASH.nextCountryFallbackModel()).toEqual(AiModel.CLAUDE_SONNET_5)
  }

  @Test
  fun `should return next country fallback model for CLAUDE_SONNET_5`() {
    expect(AiModel.CLAUDE_SONNET_5.nextCountryFallbackModel()).toEqual(AiModel.GEMINI_3_5_FLASH_LITE)
  }

  @Test
  fun `should return next country fallback model for GEMINI_3_5_FLASH_LITE`() {
    expect(AiModel.GEMINI_3_5_FLASH_LITE.nextCountryFallbackModel()).toEqual(AiModel.DEEPSEEK_V4_PRO)
  }

  @Test
  fun `should return next country fallback model for DEEPSEEK_V4_PRO`() {
    expect(AiModel.DEEPSEEK_V4_PRO.nextCountryFallbackModel()).toEqual(AiModel.GPT_5_6_TERRA)
  }

  @Test
  fun `should return next country fallback model for GPT_5_6_TERRA`() {
    expect(AiModel.GPT_5_6_TERRA.nextCountryFallbackModel()).toEqual(AiModel.CLAUDE_OPUS_5)
  }

  @Test
  fun `should return null for CLAUDE_OPUS_5 as last country fallback`() {
    expect(AiModel.CLAUDE_OPUS_5.nextCountryFallbackModel()).toEqual(null)
  }

  @Test
  fun `should have unique sector fallback tiers for all models`() {
    val tiers = AiModel.entries.map { it.sectorFallbackTier }.filter { it >= 0 }
    val uniqueTiers = tiers.toSet()

    expect(tiers.size).toEqual(uniqueTiers.size)
  }

  @Test
  fun `should have unique model ids for all models`() {
    val ids = AiModel.entries.map { it.modelId.lowercase() }

    expect(ids.size).toEqual(ids.toSet().size)
  }

  @Test
  fun `should return primary sector model`() {
    expect(AiModel.primarySectorModel()).toEqual(AiModel.DEEPSEEK_V4_FLASH)
  }

  @Test
  fun `should return primary country model`() {
    expect(AiModel.primaryCountryModel()).toEqual(AiModel.DEEPSEEK_V4_FLASH)
  }

  companion object {
    private val RETIRED =
      listOf(
        "CLAUDE_SONNET_4_6",
        "DEEPSEEK_V3_2",
        "GPT_5_4",
        "CLAUDE_OPUS_4_6",
        "GEMINI_3_FLASH_PREVIEW",
        "GPT_5_5",
        "CLAUDE_OPUS_4_8",
        "GPT_5_4_NANO",
      )
  }
}
