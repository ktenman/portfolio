package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.VisionModel
import org.junit.jupiter.api.Test

class VisionModelTest {
  @Test
  fun `should return Gemini Flash Lite as primary model`() {
    val primary = VisionModel.primary()

    expect(primary).toEqual(VisionModel.GEMINI_2_5_FLASH_LITE)
  }

  @Test
  fun `should return Pixtral as fallback for Gemini Flash Lite`() {
    val fallback = VisionModel.GEMINI_2_5_FLASH_LITE.nextFallbackModel()

    expect(fallback).toEqual(VisionModel.PIXTRAL_12B)
  }

  @Test
  fun `should return null as fallback for Pixtral`() {
    val fallback = VisionModel.PIXTRAL_12B.nextFallbackModel()

    expect(fallback).toEqual(null)
  }

  @Test
  fun `should have correct model ids`() {
    expect(VisionModel.GEMINI_2_5_FLASH_LITE.modelId).toEqual("google/gemini-2.5-flash-lite")
    expect(VisionModel.PIXTRAL_12B.modelId).toEqual("mistralai/pixtral-12b")
  }

  @Test
  fun `should have correct fallback tiers`() {
    expect(VisionModel.GEMINI_2_5_FLASH_LITE.fallbackTier).toEqual(0)
    expect(VisionModel.PIXTRAL_12B.fallbackTier).toEqual(1)
  }
}
