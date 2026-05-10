package ee.tenman.portfolio.domain

enum class VisionModel(
  val modelId: String,
) {
  LLAMA_4_SCOUT("meta-llama/llama-4-scout"),
  NOVA_LITE("amazon/nova-lite-v1"),
  ;

  companion object {
    fun openRouterModels(): List<VisionModel> = entries.toList()
  }
}
