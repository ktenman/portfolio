package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.domain.AiModel

data class TickerExtractionResult(
  val ticker: String,
  val model: AiModel?,
)
