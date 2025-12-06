package ee.tenman.portfolio.lightyear

data class ValidatedRowData(
  val name: String,
  val ticker: String?,
  val sector: String?,
  val weightText: String,
)
