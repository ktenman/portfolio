package ee.tenman.portfolio.domain

data class DiversificationConfigData(
  val allocations: List<DiversificationAllocationData>,
  val inputMode: InputMode,
  val selectedPlatform: String? = null,
  val optimizeEnabled: Boolean = false,
  val totalInvestment: Double = 0.0,
  val actionDisplayMode: ActionDisplayMode = ActionDisplayMode.UNITS,
)
