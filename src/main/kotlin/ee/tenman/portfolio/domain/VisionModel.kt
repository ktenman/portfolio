package ee.tenman.portfolio.domain

enum class VisionModel(
  val modelId: String,
  val isOpenRouter: Boolean = true,
) {
  LLAMA_4_SCOUT("meta-llama/llama-4-scout"),
  NOVA_LITE("amazon/nova-lite-v1"),
  GOOGLE_VISION("google-vision", isOpenRouter = false),
}
