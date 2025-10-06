package ee.tenman.portfolio.domain

data class EnumsResponse(
  val platforms: List<String>,
  val providers: List<String>,
  val transactionTypes: List<String>,
  val categories: List<String>,
  val currencies: List<String>,
)
