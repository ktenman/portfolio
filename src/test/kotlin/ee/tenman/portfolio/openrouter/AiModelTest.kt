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
  fun `should return GEMINI_2_5_FLASH for matching model id`() {
    val result = AiModel.fromModelId("google/gemini-2.5-flash")

    expect(result).toEqual(AiModel.GEMINI_2_5_FLASH)
  }

  @Test
  fun `should return CLAUDE_SONNET_4_5 for matching model id`() {
    val result = AiModel.fromModelId("anthropic/claude-sonnet-4.5")

    expect(result).toEqual(AiModel.CLAUDE_SONNET_4_5)
  }

  @Test
  fun `should return CLAUDE_OPUS_4_5 for matching model id`() {
    val result = AiModel.fromModelId("anthropic/claude-opus-4.5")

    expect(result).toEqual(AiModel.CLAUDE_OPUS_4_5)
  }

  @Test
  fun `should return DEEPSEEK_V3_2 for matching model id`() {
    val result = AiModel.fromModelId("deepseek/deepseek-v3.2")

    expect(result).toEqual(AiModel.DEEPSEEK_V3_2)
  }

  @Test
  fun `should return null for unknown model id`() {
    val result = AiModel.fromModelId("unknown/model")

    expect(result).toEqual(null)
  }

  @Test
  fun `should match model id case insensitively`() {
    val result = AiModel.fromModelId("GOOGLE/GEMINI-2.5-FLASH")

    expect(result).toEqual(AiModel.GEMINI_2_5_FLASH)
  }

  @Test
  fun `should have correct rate limits for GEMINI_3_FLASH_PREVIEW`() {
    expect(AiModel.GEMINI_3_FLASH_PREVIEW.rateLimitPerMinute).toEqual(100)
  }

  @Test
  fun `should have correct rate limits for GEMINI_2_5_FLASH`() {
    expect(AiModel.GEMINI_2_5_FLASH.rateLimitPerMinute).toEqual(100)
  }

  @Test
  fun `should have correct rate limits for CLAUDE_SONNET_4_5`() {
    expect(AiModel.CLAUDE_SONNET_4_5.rateLimitPerMinute).toEqual(2)
  }

  @Test
  fun `should have correct rate limits for CLAUDE_OPUS_4_5`() {
    expect(AiModel.CLAUDE_OPUS_4_5.rateLimitPerMinute).toEqual(1)
  }

  @Test
  fun `should have correct rate limits for DEEPSEEK_V3_2`() {
    expect(AiModel.DEEPSEEK_V3_2.rateLimitPerMinute).toEqual(60)
  }

  @Test
  fun `should have correct sector fallback tiers`() {
    expect(AiModel.GEMINI_3_FLASH_PREVIEW.sectorFallbackTier).toEqual(0)
    expect(AiModel.CLAUDE_OPUS_4_5.sectorFallbackTier).toEqual(1)
    expect(AiModel.CLAUDE_SONNET_4_5.sectorFallbackTier).toEqual(2)
    expect(AiModel.GEMINI_2_5_FLASH.sectorFallbackTier).toEqual(3)
    expect(AiModel.DEEPSEEK_V3_2.sectorFallbackTier).toEqual(4)
  }

  @Test
  fun `should return next sector fallback model for GEMINI_3_FLASH_PREVIEW`() {
    expect(AiModel.GEMINI_3_FLASH_PREVIEW.nextSectorFallbackModel()).toEqual(AiModel.CLAUDE_OPUS_4_5)
  }

  @Test
  fun `should return next sector fallback model for CLAUDE_OPUS_4_5`() {
    expect(AiModel.CLAUDE_OPUS_4_5.nextSectorFallbackModel()).toEqual(AiModel.CLAUDE_SONNET_4_5)
  }

  @Test
  fun `should return next sector fallback model for CLAUDE_SONNET_4_5`() {
    expect(AiModel.CLAUDE_SONNET_4_5.nextSectorFallbackModel()).toEqual(AiModel.GEMINI_2_5_FLASH)
  }

  @Test
  fun `should return next sector fallback model for GEMINI_2_5_FLASH`() {
    expect(AiModel.GEMINI_2_5_FLASH.nextSectorFallbackModel()).toEqual(AiModel.DEEPSEEK_V3_2)
  }

  @Test
  fun `should return null for DEEPSEEK_V3_2 as last sector fallback`() {
    expect(AiModel.DEEPSEEK_V3_2.nextSectorFallbackModel()).toEqual(null)
  }

  @Test
  fun `should have correct country fallback tiers`() {
    expect(AiModel.CLAUDE_OPUS_4_5.countryFallbackTier).toEqual(0)
    expect(AiModel.CLAUDE_SONNET_4_5.countryFallbackTier).toEqual(1)
    expect(AiModel.DEEPSEEK_V3_2.countryFallbackTier).toEqual(2)
  }

  @Test
  fun `should return next country fallback model for CLAUDE_OPUS_4_5`() {
    expect(AiModel.CLAUDE_OPUS_4_5.nextCountryFallbackModel()).toEqual(AiModel.CLAUDE_SONNET_4_5)
  }

  @Test
  fun `should return next country fallback model for CLAUDE_SONNET_4_5`() {
    expect(AiModel.CLAUDE_SONNET_4_5.nextCountryFallbackModel()).toEqual(AiModel.DEEPSEEK_V3_2)
  }

  @Test
  fun `should return null for DEEPSEEK_V3_2 as last country fallback`() {
    expect(AiModel.DEEPSEEK_V3_2.nextCountryFallbackModel()).toEqual(null)
  }

  @Test
  fun `should have unique sector fallback tiers for all models`() {
    val tiers = AiModel.entries.map { it.sectorFallbackTier }.filter { it >= 0 }
    val uniqueTiers = tiers.toSet()

    expect(tiers.size).toEqual(uniqueTiers.size)
  }

  @Test
  fun `should return primary sector model`() {
    expect(AiModel.primarySectorModel()).toEqual(AiModel.GEMINI_3_FLASH_PREVIEW)
  }

  @Test
  fun `should return primary country model`() {
    expect(AiModel.primaryCountryModel()).toEqual(AiModel.CLAUDE_OPUS_4_5)
  }
}
