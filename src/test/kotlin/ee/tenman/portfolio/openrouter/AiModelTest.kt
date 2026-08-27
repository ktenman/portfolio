package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.AiModel
import org.junit.jupiter.api.Test

class AiModelTest {
  @Test
  fun `should resolve every model id back to its own constant`() {
    val mismatched = AiModel.entries.filter { AiModel.fromModelId(it.modelId) != it }

    expect(mismatched).toBeEmpty()
  }

  @Test
  fun `should match model id case insensitively`() {
    val model = AiModel.primarySectorModel()

    expect(AiModel.fromModelId(model.modelId.uppercase())).toEqual(model)
  }

  @Test
  fun `should return null for unknown model id`() {
    expect(AiModel.fromModelId("unknown/model")).toEqual(null)
  }

  @Test
  fun `should have unique model ids`() {
    val ids = AiModel.entries.map { it.modelId.lowercase() }

    expect(ids.toSet()).toHaveSize(ids.size)
  }

  @Test
  fun `should keep every off cascade model resolvable so stored classifications cannot break`() {
    val offCascade =
      AiModel.entries
        .filter { it.sectorFallbackTier < 0 && it.countryFallbackTier < 0 }
        .map { it.name }
        .toSet()

    expect(offCascade).toEqual(OFF_CASCADE)
  }

  @Test
  fun `should chain the sector cascade through contiguous tiers down to a null terminator`() {
    val walked = generateSequence(AiModel.primarySectorModel()) { it.nextSectorFallbackModel() }.toList()

    expect(walked).toEqual(rankedBy { it.sectorFallbackTier })
  }

  @Test
  fun `should chain the country cascade through contiguous tiers down to a null terminator`() {
    val walked = generateSequence(AiModel.primaryCountryModel()) { it.nextCountryFallbackModel() }.toList()

    expect(walked).toEqual(rankedBy { it.countryFallbackTier })
  }

  @Test
  fun `should rate limit every model in a cascade`() {
    val unlimited = AiModel.entries.filter { it.sectorFallbackTier >= 0 && it.rateLimitPerMinute <= 0 }

    expect(unlimited).toBeEmpty()
  }

  private fun rankedBy(tier: (AiModel) -> Int): List<AiModel> = AiModel.entries.filter { tier(it) >= 0 }.sortedBy(tier)

  companion object {
    private val OFF_CASCADE =
      setOf(
        "CLAUDE_SONNET_4_6",
        "DEEPSEEK_V3_2",
        "GPT_5_4",
        "CLAUDE_OPUS_4_6",
        "GEMINI_3_FLASH_PREVIEW",
        "GPT_5_5",
        "CLAUDE_OPUS_4_8",
        "GPT_5_4_NANO",
        "DEEPSEEK_V4_FLASH",
        "GEMINI_3_5_FLASH_LITE",
      )
  }
}
