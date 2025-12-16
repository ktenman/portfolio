package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.VisionModel
import org.junit.jupiter.api.Test

class VisionModelTest {
  @Test
  fun `should return Pixtral and Gemini Flash as OpenRouter models`() {
    val models = VisionModel.openRouterModels()

    expect(models).toContainExactly(VisionModel.PIXTRAL_12B, VisionModel.GEMINI_2_5_FLASH)
  }

  @Test
  fun `should have correct model ids`() {
    expect(VisionModel.PIXTRAL_12B.modelId).toEqual("mistralai/pixtral-12b")
    expect(VisionModel.GEMINI_2_5_FLASH.modelId).toEqual("google/gemini-2.5-flash")
  }
}
