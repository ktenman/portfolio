package ee.tenman.portfolio.model

data class HoldingMergePlan(
  val canonicalId: Long,
  val canonicalName: String,
  val duplicateIds: List<Long>,
)
