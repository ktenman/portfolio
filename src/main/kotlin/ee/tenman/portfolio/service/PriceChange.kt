package ee.tenman.portfolio.service

import java.math.BigDecimal

data class PriceChange(
  val changeAmount: BigDecimal,
  val changePercent: Double,
)
