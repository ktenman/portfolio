package ee.tenman.portfolio.domain

enum class DetectionProvider(
  val displayName: String,
) {
  LLAMA_4_SCOUT("meta-llama/llama-4-scout"),
  NOVA_LITE("amazon/nova-lite-v1"),
  ALL_FAILED("All providers failed"),
  ;

  companion object {
    fun fromVisionModel(model: VisionModel): DetectionProvider =
      when (model) {
        VisionModel.LLAMA_4_SCOUT -> LLAMA_4_SCOUT
        VisionModel.NOVA_LITE -> NOVA_LITE
      }
  }
}
