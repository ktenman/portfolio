package ee.tenman.portfolio.service.integration

import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.GicsIndustry
import ee.tenman.portfolio.domain.IndustrySector

data class SectorClassificationResult(
  val sector: IndustrySector,
  val model: AiModel?,
)

data class GicsIndustryClassificationResult(
  val industry: GicsIndustry,
  val model: AiModel?,
)
