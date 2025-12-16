package ee.tenman.portfolio.domain

enum class VisionModel(
  val modelId: String,
) {
  PIXTRAL_12B("mistralai/pixtral-12b"),
  GEMINI_2_5_FLASH("google/gemini-2.5-flash"),
  ;

  companion object {
    fun openRouterModels(): List<VisionModel> = listOf(PIXTRAL_12B, GEMINI_2_5_FLASH)
  }
}
