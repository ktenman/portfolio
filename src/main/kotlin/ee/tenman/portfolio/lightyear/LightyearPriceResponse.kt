package ee.tenman.portfolio.lightyear

import java.math.BigDecimal

data class LightyearPriceResponse(
  val timestamp: String,
  val price: BigDecimal,
  val change: BigDecimal,
  val changePercent: BigDecimal,
  val currency: String,
)
