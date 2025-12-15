package ee.tenman.portfolio.auto24

data class Auto24PriceResponse(
  val registrationNumber: String,
  val marketPrice: String?,
  val error: String?,
  val attempts: Int?,
)
