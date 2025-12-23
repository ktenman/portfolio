package ee.tenman.portfolio.lightyear

data class LightyearInstrumentResponse(
  val id: String,
  val symbol: String,
  val name: String,
  val exchange: String?,
  val logo: String?,
)
