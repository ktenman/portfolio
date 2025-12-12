package ee.tenman.portfolio.domain

enum class DetectionProvider(
  val displayName: String,
) {
  GOOGLE_VISION("Google Vision"),
  GEMINI_FLASH_LITE("google/gemini-2.5-flash-lite"),
  PIXTRAL("mistralai/pixtral-12b"),
  ALL_FAILED("All providers failed"),
  ;

  companion object {
    fun fromVisionModel(model: VisionModel): DetectionProvider =
      when (model) {
        VisionModel.GEMINI_2_5_FLASH_LITE -> GEMINI_FLASH_LITE
        VisionModel.PIXTRAL_12B -> PIXTRAL
      }
  }
}
