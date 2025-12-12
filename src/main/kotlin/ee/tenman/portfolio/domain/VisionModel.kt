package ee.tenman.portfolio.domain

enum class VisionModel(
  val modelId: String,
  val fallbackTier: Int,
) {
  GEMINI_2_5_FLASH_LITE("google/gemini-2.5-flash-lite", 0),
  PIXTRAL_12B("mistralai/pixtral-12b", 1),
  ;

  fun nextFallbackModel(): VisionModel? = entries.find { it.fallbackTier == this.fallbackTier + 1 }

  companion object {
    fun primary(): VisionModel = GEMINI_2_5_FLASH_LITE
  }
}
