package ee.tenman.portfolio.domain

import java.math.BigDecimal

data class DiversificationAllocationData(
  val instrumentId: Long,
  val value: BigDecimal,
)

data class DiversificationConfigData(
  val allocations: List<DiversificationAllocationData>,
  val inputMode: InputMode,
  val selectedPlatforms: List<String> = emptyList(),
  val optimizeEnabled: Boolean = false,
  val totalInvestment: Double = 0.0,
  val actionDisplayMode: ActionDisplayMode = ActionDisplayMode.UNITS,
  val buyOnlyEnabled: Boolean = false,
)
