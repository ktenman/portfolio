package ee.tenman.portfolio.domain

import java.time.LocalDate
import java.util.UUID

enum class IndustrySource {
  LLM,
  VANGUARD,
  UNKNOWN,
}

enum class CountrySource {
  LLM,
  RULE,
  VANGUARD,
  UNKNOWN,
}

data class VanguardIndustryUpdate(
  val holdingUuid: UUID,
  val industry: GicsIndustry,
  val effectiveDate: LocalDate,
)

data class VanguardCountryUpdate(
  val holdingUuid: UUID,
  val countryCode: String,
  val effectiveDate: LocalDate,
)

data class VanguardHoldingUpdates(
  val industries: List<VanguardIndustryUpdate>,
  val countries: List<VanguardCountryUpdate>,
)
