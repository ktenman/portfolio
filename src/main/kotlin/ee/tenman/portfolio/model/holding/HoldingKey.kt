package ee.tenman.portfolio.model.holding

data class HoldingKey(
  val holdingId: Long,
  val ticker: String?,
  val name: String,
  val sector: String?,
  val countryCode: String?,
  val countryName: String?,
  val isSynthetic: Boolean = false,
)
