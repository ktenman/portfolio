package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.VisionModel
import org.junit.jupiter.api.Test

class VisionModelTest {
  @Test
  fun `should have correct model ids`() {
    expect(VisionModel.LLAMA_4_SCOUT.modelId).toEqual("meta-llama/llama-4-scout")
    expect(VisionModel.NOVA_LITE.modelId).toEqual("amazon/nova-lite-v1")
  }
}
