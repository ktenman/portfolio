package ee.tenman.portfolio.auto24

data class CarPriceResult(
  val price: String?,
  val error: String? = null,
  val durationSeconds: Double? = null,
)
