package ee.tenman.portfolio.lightyear

data class LightyearInstrumentResponse(
  val id: String,
  val symbol: String,
  val name: String,
  val exchange: String?,
  val logo: String?,
  val summary: LightyearInstrumentSummary? = null,
)

data class LightyearInstrumentSummary(
  val sector: String? = null,
)
