package ee.tenman.portfolio.service.integration

import ee.tenman.portfolio.domain.AiModel

data class CountryClassificationResult(
  val countryCode: String,
  val countryName: String,
  val model: AiModel?,
)
