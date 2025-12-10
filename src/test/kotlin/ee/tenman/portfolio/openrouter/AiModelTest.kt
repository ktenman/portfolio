package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.AiModel
import org.junit.jupiter.api.Test

class AiModelTest {
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
    val result = AiModel.fromModelId("ANTHROPIC/CLAUDE-3-HAIKU")

    expect(result).toEqual(AiModel.CLAUDE_3_HAIKU)
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
    expect(AiModel.CLAUDE_3_HAIKU.fallbackTier).toEqual(0)
    expect(AiModel.CLAUDE_HAIKU_4_5.fallbackTier).toEqual(1)
    expect(AiModel.CLAUDE_SONNET_4_5.fallbackTier).toEqual(2)
    expect(AiModel.CLAUDE_OPUS_4_5.fallbackTier).toEqual(3)
  }

  @Test
  fun `should return next fallback model for CLAUDE_3_HAIKU`() {
    expect(AiModel.CLAUDE_3_HAIKU.getNextFallback()).toEqual(AiModel.CLAUDE_HAIKU_4_5)
  }

  @Test
  fun `should return next fallback model for CLAUDE_HAIKU_4_5`() {
    expect(AiModel.CLAUDE_HAIKU_4_5.getNextFallback()).toEqual(AiModel.CLAUDE_SONNET_4_5)
  }

  @Test
  fun `should return next fallback model for CLAUDE_SONNET_4_5`() {
    expect(AiModel.CLAUDE_SONNET_4_5.getNextFallback()).toEqual(AiModel.CLAUDE_OPUS_4_5)
  }

  @Test
  fun `should return null for CLAUDE_OPUS_4_5 as last fallback`() {
    expect(AiModel.CLAUDE_OPUS_4_5.getNextFallback()).toEqual(null)
  }

  @Test
  fun `should have unique fallback tiers for all models`() {
    val tiers = AiModel.entries.map { it.fallbackTier }
    val uniqueTiers = tiers.toSet()

    expect(tiers.size).toEqual(uniqueTiers.size)
  }
}
