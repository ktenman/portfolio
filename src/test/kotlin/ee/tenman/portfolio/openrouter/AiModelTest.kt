package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.AiModel
import org.junit.jupiter.api.Test

class AiModelTest {
  @Test
  fun `should return GEMINI_3_FLASH_PREVIEW for matching model id`() {
    val result = AiModel.fromModelId("google/gemini-3-flash-preview")

    expect(result).toEqual(AiModel.GEMINI_3_FLASH_PREVIEW)
  }

  @Test
  fun `should return CLAUDE_SONNET_5 for matching model id`() {
    val result = AiModel.fromModelId("anthropic/claude-sonnet-5")

    expect(result).toEqual(AiModel.CLAUDE_SONNET_5)
  }

  @Test
  fun `should return CLAUDE_OPUS_4_8 for matching model id`() {
    val result = AiModel.fromModelId("anthropic/claude-opus-4.8")

    expect(result).toEqual(AiModel.CLAUDE_OPUS_4_8)
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
  fun `should return GPT_5_5 for matching model id`() {
    val result = AiModel.fromModelId("openai/gpt-5.5")

    expect(result).toEqual(AiModel.GPT_5_5)
  }

  @Test
  fun `should return GPT_5_4_NANO for matching model id`() {
    val result = AiModel.fromModelId("openai/gpt-5.4-nano")

    expect(result).toEqual(AiModel.GPT_5_4_NANO)
    expect(AiModel.GPT_5_4_NANO.modelId).toEqual("openai/gpt-5.4-nano")
  }

  @Test
  fun `should return null for unknown model id`() {
    val result = AiModel.fromModelId("unknown/model")

    expect(result).toEqual(null)
  }

  @Test
  fun `should match model id case insensitively`() {
    val result = AiModel.fromModelId("GOOGLE/GEMINI-3-FLASH-PREVIEW")

    expect(result).toEqual(AiModel.GEMINI_3_FLASH_PREVIEW)
  }

  @Test
  fun `should keep retired model names resolvable for stored classifications`() {
    val retired = listOf("CLAUDE_SONNET_4_6", "DEEPSEEK_V3_2", "GPT_5_4", "CLAUDE_OPUS_4_6")

    expect(retired.map { AiModel.valueOf(it).sectorFallbackTier }.toSet()).toEqual(setOf(-1))
  }

  @Test
  fun `should keep retired model names out of country cascade`() {
    val retired = listOf("CLAUDE_SONNET_4_6", "DEEPSEEK_V3_2", "GPT_5_4", "CLAUDE_OPUS_4_6")

    expect(retired.map { AiModel.valueOf(it).countryFallbackTier }.toSet()).toEqual(setOf(-1))
  }

  @Test
  fun `should have correct rate limits for GEMINI_3_FLASH_PREVIEW`() {
    expect(AiModel.GEMINI_3_FLASH_PREVIEW.rateLimitPerMinute).toEqual(400)
  }

  @Test
  fun `should have correct rate limits for CLAUDE_SONNET_5`() {
    expect(AiModel.CLAUDE_SONNET_5.rateLimitPerMinute).toEqual(240)
  }

  @Test
  fun `should have correct rate limits for CLAUDE_OPUS_4_8`() {
    expect(AiModel.CLAUDE_OPUS_4_8.rateLimitPerMinute).toEqual(240)
  }

  @Test
  fun `should have correct rate limits for DEEPSEEK_V4_PRO`() {
    expect(AiModel.DEEPSEEK_V4_PRO.rateLimitPerMinute).toEqual(240)
  }

  @Test
  fun `should have correct rate limits for GPT_5_5`() {
    expect(AiModel.GPT_5_5.rateLimitPerMinute).toEqual(240)
  }

  @Test
  fun `should have correct rate limits for DEEPSEEK_V4_FLASH`() {
    expect(AiModel.DEEPSEEK_V4_FLASH.rateLimitPerMinute).toEqual(240)
  }

  @Test
  fun `should have correct sector fallback tiers`() {
    expect(AiModel.DEEPSEEK_V4_FLASH.sectorFallbackTier).toEqual(0)
    expect(AiModel.GEMINI_3_FLASH_PREVIEW.sectorFallbackTier).toEqual(1)
    expect(AiModel.CLAUDE_SONNET_5.sectorFallbackTier).toEqual(2)
    expect(AiModel.DEEPSEEK_V4_PRO.sectorFallbackTier).toEqual(3)
    expect(AiModel.GPT_5_5.sectorFallbackTier).toEqual(4)
    expect(AiModel.CLAUDE_OPUS_4_8.sectorFallbackTier).toEqual(5)
  }

  @Test
  fun `should return next sector fallback model for DEEPSEEK_V4_FLASH`() {
    expect(AiModel.DEEPSEEK_V4_FLASH.nextSectorFallbackModel()).toEqual(AiModel.GEMINI_3_FLASH_PREVIEW)
  }

  @Test
  fun `should return next sector fallback model for GEMINI_3_FLASH_PREVIEW`() {
    expect(AiModel.GEMINI_3_FLASH_PREVIEW.nextSectorFallbackModel()).toEqual(AiModel.CLAUDE_SONNET_5)
  }

  @Test
  fun `should return next sector fallback model for CLAUDE_SONNET_5`() {
    expect(AiModel.CLAUDE_SONNET_5.nextSectorFallbackModel()).toEqual(AiModel.DEEPSEEK_V4_PRO)
  }

  @Test
  fun `should return next sector fallback model for DEEPSEEK_V4_PRO`() {
    expect(AiModel.DEEPSEEK_V4_PRO.nextSectorFallbackModel()).toEqual(AiModel.GPT_5_5)
  }

  @Test
  fun `should return next sector fallback model for GPT_5_5`() {
    expect(AiModel.GPT_5_5.nextSectorFallbackModel()).toEqual(AiModel.CLAUDE_OPUS_4_8)
  }

  @Test
  fun `should return null for CLAUDE_OPUS_4_8 as last sector fallback`() {
    expect(AiModel.CLAUDE_OPUS_4_8.nextSectorFallbackModel()).toEqual(null)
  }

  @Test
  fun `should have correct country fallback tiers`() {
    expect(AiModel.DEEPSEEK_V4_FLASH.countryFallbackTier).toEqual(0)
    expect(AiModel.CLAUDE_SONNET_5.countryFallbackTier).toEqual(1)
    expect(AiModel.GEMINI_3_FLASH_PREVIEW.countryFallbackTier).toEqual(2)
    expect(AiModel.DEEPSEEK_V4_PRO.countryFallbackTier).toEqual(3)
    expect(AiModel.GPT_5_5.countryFallbackTier).toEqual(4)
    expect(AiModel.CLAUDE_OPUS_4_8.countryFallbackTier).toEqual(5)
  }

  @Test
  fun `should return next country fallback model for DEEPSEEK_V4_FLASH`() {
    expect(AiModel.DEEPSEEK_V4_FLASH.nextCountryFallbackModel()).toEqual(AiModel.CLAUDE_SONNET_5)
  }

  @Test
  fun `should return next country fallback model for CLAUDE_SONNET_5`() {
    expect(AiModel.CLAUDE_SONNET_5.nextCountryFallbackModel()).toEqual(AiModel.GEMINI_3_FLASH_PREVIEW)
  }

  @Test
  fun `should return next country fallback model for GEMINI_3_FLASH_PREVIEW`() {
    expect(AiModel.GEMINI_3_FLASH_PREVIEW.nextCountryFallbackModel()).toEqual(AiModel.DEEPSEEK_V4_PRO)
  }

  @Test
  fun `should return next country fallback model for DEEPSEEK_V4_PRO`() {
    expect(AiModel.DEEPSEEK_V4_PRO.nextCountryFallbackModel()).toEqual(AiModel.GPT_5_5)
  }

  @Test
  fun `should return next country fallback model for GPT_5_5`() {
    expect(AiModel.GPT_5_5.nextCountryFallbackModel()).toEqual(AiModel.CLAUDE_OPUS_4_8)
  }

  @Test
  fun `should return null for CLAUDE_OPUS_4_8 as last country fallback`() {
    expect(AiModel.CLAUDE_OPUS_4_8.nextCountryFallbackModel()).toEqual(null)
  }

  @Test
  fun `should have unique sector fallback tiers for all models`() {
    val tiers = AiModel.entries.map { it.sectorFallbackTier }.filter { it >= 0 }
    val uniqueTiers = tiers.toSet()

    expect(tiers.size).toEqual(uniqueTiers.size)
  }

  @Test
  fun `should return primary sector model`() {
    expect(AiModel.primarySectorModel()).toEqual(AiModel.DEEPSEEK_V4_FLASH)
  }

  @Test
  fun `should return primary country model`() {
    expect(AiModel.primaryCountryModel()).toEqual(AiModel.DEEPSEEK_V4_FLASH)
  }
}
