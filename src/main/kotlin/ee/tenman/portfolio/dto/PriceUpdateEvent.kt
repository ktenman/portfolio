package ee.tenman.portfolio.dto

data class PriceUpdateEvent(
  val type: String,
  val message: String,
  val updatedCount: Int,
  val timestamp: Long = System.currentTimeMillis(),
)
