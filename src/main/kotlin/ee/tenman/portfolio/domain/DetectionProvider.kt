package ee.tenman.portfolio.domain

enum class DetectionProvider(
  val displayName: String,
) {
  GOOGLE_VISION("Google Vision"),
  GEMINI_FLASH("google/gemini-2.5-flash"),
  PIXTRAL("mistralai/pixtral-12b"),
  ALL_FAILED("All providers failed"),
  ;

  companion object {
    fun fromVisionModel(model: VisionModel): DetectionProvider =
      when (model) {
        VisionModel.PIXTRAL_12B -> PIXTRAL
        VisionModel.GEMINI_2_5_FLASH -> GEMINI_FLASH
      }
  }
}
