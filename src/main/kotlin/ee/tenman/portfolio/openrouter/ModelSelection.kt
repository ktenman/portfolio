package ee.tenman.portfolio.openrouter

import ee.tenman.portfolio.domain.AiModel

data class ModelSelection(
  val model: AiModel,
  val fallbackTier: Int,
) {
  val modelId: String get() = model.modelId
  val isUsingFallback: Boolean get() = fallbackTier > 0
}
