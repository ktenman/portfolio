package ee.tenman.portfolio.service.transaction

data class TransactionStats(
  val count: Int,
  val platforms: List<String>,
)
