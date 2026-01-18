package ee.tenman.portfolio.model.holding

import java.math.BigDecimal

data class CurrentHoldings(
  val quantity: BigDecimal,
  val averageCost: BigDecimal,
)
