package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.VisionModel
import org.junit.jupiter.api.Test

class VisionModelTest {
  @Test
  fun `should return all vision models as OpenRouter models`() {
    val models = VisionModel.openRouterModels()

    expect(models).toContainExactly(
      VisionModel.LLAMA_90B_VISION,
      VisionModel.PIXTRAL_12B,
    )
  }

  @Test
  fun `should have correct model ids`() {
    expect(VisionModel.LLAMA_90B_VISION.modelId).toEqual("meta-llama/llama-3.2-90b-vision-instruct")
    expect(VisionModel.PIXTRAL_12B.modelId).toEqual("mistralai/pixtral-12b")
  }
}
