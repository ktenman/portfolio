package ee.tenman.portfolio.domain

enum class DetectionProvider(
  val displayName: String,
) {
  GOOGLE_VISION("Google Vision"),
  LLAMA_90B("meta-llama/llama-3.2-90b-vision-instruct"),
  PIXTRAL("mistralai/pixtral-12b"),
  ALL_FAILED("All providers failed"),
  ;

  companion object {
    fun fromVisionModel(model: VisionModel): DetectionProvider =
      when (model) {
        VisionModel.LLAMA_90B_VISION -> LLAMA_90B
        VisionModel.PIXTRAL_12B -> PIXTRAL
      }
  }
}
