package ee.tenman.portfolio.domain

data class DiversificationConfigData(
  val allocations: List<DiversificationAllocationData>,
  val inputMode: InputMode,
)
