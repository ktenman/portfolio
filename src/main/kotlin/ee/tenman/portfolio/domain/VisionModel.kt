package ee.tenman.portfolio.domain

enum class VisionModel(
  val modelId: String,
) {
  LLAMA_90B_VISION("meta-llama/llama-3.2-90b-vision-instruct"),
  PIXTRAL_12B("mistralai/pixtral-12b"),
  ;

  companion object {
    fun openRouterModels(): List<VisionModel> = entries.toList()
  }
}
