package ee.tenman.portfolio.model.holding

import java.math.BigDecimal

data class AggregatedHoldings(
  val totalQuantity: BigDecimal,
  val totalInvestment: BigDecimal,
)
