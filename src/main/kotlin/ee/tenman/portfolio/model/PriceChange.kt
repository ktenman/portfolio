package ee.tenman.portfolio.model

import java.math.BigDecimal

data class PriceChange(
  val changeAmount: BigDecimal,
  val changePercent: Double,
)
