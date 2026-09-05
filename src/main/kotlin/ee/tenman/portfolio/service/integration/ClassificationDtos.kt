package ee.tenman.portfolio.service.integration

import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.GicsIndustry
import ee.tenman.portfolio.domain.IndustrySector

data class CompanyClassificationInput(
  val holdingId: Long,
  val name: String,
  val ticker: String?,
  val etfNames: List<String> = emptyList(),
)

data class BatchClassificationOutcome<T>(
  val results: Map<Long, T>,
  val llmAnswered: Boolean,
)

data class SectorClassificationResult(
  val sector: IndustrySector,
  val model: AiModel?,
)

data class CountryClassificationResult(
  val countryCode: String,
  val countryName: String,
  val model: AiModel?,
)

data class GicsIndustryClassificationResult(
  val industry: GicsIndustry,
  val model: AiModel?,
)
