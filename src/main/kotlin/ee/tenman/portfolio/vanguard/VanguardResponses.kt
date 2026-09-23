package ee.tenman.portfolio.vanguard

import ee.tenman.portfolio.domain.GicsIndustry
import ee.tenman.portfolio.dto.HoldingData
import java.math.BigDecimal
import java.time.LocalDate

data class VanguardHoldingsRequest(
  val query: String,
  val variables: VanguardHoldingsVariables,
)

data class VanguardHoldingsVariables(
  val portIds: List<String>,
  val securityTypes: List<String>,
  val lastItemKey: String?,
)

data class VanguardHoldingsResponse(
  val data: VanguardHoldingsPayload? = null,
  val errors: List<VanguardError>? = null,
)

data class VanguardError(
  val message: String? = null,
)

data class VanguardHoldingsPayload(
  val borHoldings: List<VanguardFund>? = null,
)

data class VanguardFund(
  val holdings: VanguardHoldingsPage? = null,
)

data class VanguardHoldingsPage(
  val items: List<VanguardHoldingItem>? = null,
  val lastItemKey: String? = null,
)

data class VanguardHoldingItem(
  val issuerName: String? = null,
  val ticker: String? = null,
  val marketValuePercentage: BigDecimal? = null,
  val gicsIndustryDescription: String? = null,
  val effectiveDate: LocalDate? = null,
  val bloombergIsoCountry: String? = null,
)

data class VanguardHolding(
  val name: String,
  val ticker: String?,
  val weight: BigDecimal,
  val effectiveDate: LocalDate,
  val industry: GicsIndustry?,
  val countryCodes: Set<String> = emptySet(),
)

data class VanguardFundSnapshot(
  val effectiveDate: LocalDate,
  val holdings: List<HoldingData>,
  val countryCodes: Map<String, Set<String>> = emptyMap(),
) {
  fun holdingsWithoutIndustries(): List<HoldingData> = holdings.map { it.copy(industry = null) }
}
