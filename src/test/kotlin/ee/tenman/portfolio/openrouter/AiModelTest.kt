package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.AiModel
import org.junit.jupiter.api.Test

class AiModelTest {
  @Test
  fun `should return GROK_4_1_FAST for matching model id`() {
    val result = AiModel.fromModelId("x-ai/grok-4.1-fast")

    expect(result).toEqual(AiModel.GROK_4_1_FAST)
  }

  @Test
  fun `should return GROK_4 for matching model id`() {
    val result = AiModel.fromModelId("x-ai/grok-4")

    expect(result).toEqual(AiModel.GROK_4)
  }

  @Test
  fun `should return GEMINI_2_5_FLASH for matching model id`() {
    val result = AiModel.fromModelId("google/gemini-2.5-flash")

    expect(result).toEqual(AiModel.GEMINI_2_5_FLASH)
  }

  @Test
  fun `should return GEMINI_3_PRO_PREVIEW for matching model id`() {
    val result = AiModel.fromModelId("google/gemini-3-pro-preview")

    expect(result).toEqual(AiModel.GEMINI_3_PRO_PREVIEW)
  }

  @Test
  fun `should return CLAUDE_3_HAIKU for matching model id`() {
    val result = AiModel.fromModelId("anthropic/claude-3-haiku")

    expect(result).toEqual(AiModel.CLAUDE_3_HAIKU)
  }

  @Test
  fun `should return CLAUDE_HAIKU_4_5 for matching model id`() {
    val result = AiModel.fromModelId("anthropic/claude-haiku-4.5")

    expect(result).toEqual(AiModel.CLAUDE_HAIKU_4_5)
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
  fun `should have correct rate limits for GROK_4_1_FAST`() {
    expect(AiModel.GROK_4_1_FAST.rateLimitPerMinute).toEqual(60)
  }

  @Test
  fun `should have correct rate limits for GROK_4`() {
    expect(AiModel.GROK_4.rateLimitPerMinute).toEqual(2)
  }

  @Test
  fun `should have correct rate limits for GEMINI_2_5_FLASH`() {
    expect(AiModel.GEMINI_2_5_FLASH.rateLimitPerMinute).toEqual(100)
  }

  @Test
  fun `should have correct rate limits for GEMINI_3_PRO_PREVIEW`() {
    expect(AiModel.GEMINI_3_PRO_PREVIEW.rateLimitPerMinute).toEqual(2)
  }

  @Test
  fun `should have correct rate limits for CLAUDE_3_HAIKU`() {
    expect(AiModel.CLAUDE_3_HAIKU.rateLimitPerMinute).toEqual(30)
  }

  @Test
  fun `should have correct rate limits for CLAUDE_HAIKU_4_5`() {
    expect(AiModel.CLAUDE_HAIKU_4_5.rateLimitPerMinute).toEqual(7)
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
  fun `should have correct fallback tiers`() {
    expect(AiModel.GEMINI_2_5_FLASH.fallbackTier).toEqual(0)
    expect(AiModel.GROK_4_1_FAST.fallbackTier).toEqual(1)
    expect(AiModel.CLAUDE_3_HAIKU.fallbackTier).toEqual(2)
    expect(AiModel.CLAUDE_HAIKU_4_5.fallbackTier).toEqual(3)
    expect(AiModel.GEMINI_3_PRO_PREVIEW.fallbackTier).toEqual(4)
    expect(AiModel.GROK_4.fallbackTier).toEqual(5)
    expect(AiModel.CLAUDE_SONNET_4_5.fallbackTier).toEqual(6)
    expect(AiModel.CLAUDE_OPUS_4_5.fallbackTier).toEqual(7)
  }

  @Test
  fun `should return next fallback model for GEMINI_2_5_FLASH`() {
    expect(AiModel.GEMINI_2_5_FLASH.nextFallbackModel()).toEqual(AiModel.GROK_4_1_FAST)
  }

  @Test
  fun `should return next fallback model for GROK_4_1_FAST`() {
    expect(AiModel.GROK_4_1_FAST.nextFallbackModel()).toEqual(AiModel.CLAUDE_3_HAIKU)
  }

  @Test
  fun `should return next fallback model for CLAUDE_3_HAIKU`() {
    expect(AiModel.CLAUDE_3_HAIKU.nextFallbackModel()).toEqual(AiModel.CLAUDE_HAIKU_4_5)
  }

  @Test
  fun `should return next fallback model for CLAUDE_HAIKU_4_5`() {
    expect(AiModel.CLAUDE_HAIKU_4_5.nextFallbackModel()).toEqual(AiModel.GEMINI_3_PRO_PREVIEW)
  }

  @Test
  fun `should return next fallback model for GEMINI_3_PRO_PREVIEW`() {
    expect(AiModel.GEMINI_3_PRO_PREVIEW.nextFallbackModel()).toEqual(AiModel.GROK_4)
  }

  @Test
  fun `should return next fallback model for GROK_4`() {
    expect(AiModel.GROK_4.nextFallbackModel()).toEqual(AiModel.CLAUDE_SONNET_4_5)
  }

  @Test
  fun `should return next fallback model for CLAUDE_SONNET_4_5`() {
    expect(AiModel.CLAUDE_SONNET_4_5.nextFallbackModel()).toEqual(AiModel.CLAUDE_OPUS_4_5)
  }

  @Test
  fun `should return null for CLAUDE_OPUS_4_5 as last fallback`() {
    expect(AiModel.CLAUDE_OPUS_4_5.nextFallbackModel()).toEqual(null)
  }

  @Test
  fun `should have unique fallback tiers for all models`() {
    val tiers = AiModel.entries.map { it.fallbackTier }
    val uniqueTiers = tiers.toSet()

    expect(tiers.size).toEqual(uniqueTiers.size)
  }
}
