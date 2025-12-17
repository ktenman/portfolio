package ee.tenman.portfolio.domain

enum class VisionModel(
  val modelId: String,
) {
  GEMINI_FLASH("google/gemini-2.5-flash"),
  PIXTRAL_12B("mistralai/pixtral-12b"),
  LLAMA_90B_VISION("meta-llama/llama-3.2-90b-vision-instruct"),
  ;

  companion object {
    fun openRouterModels(): List<VisionModel> = entries.toList()

    fun fastModels(): List<VisionModel> = listOf(GEMINI_FLASH, PIXTRAL_12B)
  }
}
