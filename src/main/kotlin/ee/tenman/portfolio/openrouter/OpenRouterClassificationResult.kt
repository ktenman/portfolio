package ee.tenman.portfolio.openrouter

import ee.tenman.portfolio.domain.AiModel

data class OpenRouterClassificationResult(
  val content: String?,
  val model: AiModel?,
)
