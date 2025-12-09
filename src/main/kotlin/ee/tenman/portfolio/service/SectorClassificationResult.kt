package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.IndustrySector

data class SectorClassificationResult(
  val sector: IndustrySector,
  val model: AiModel?,
)
