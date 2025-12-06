package ee.tenman.portfolio.model.metrics

import java.math.BigDecimal

data class InstrumentMetrics(
  val totalInvestment: BigDecimal,
  val currentValue: BigDecimal,
  val profit: BigDecimal,
  val realizedProfit: BigDecimal,
  val unrealizedProfit: BigDecimal,
  val xirr: Double,
  val quantity: BigDecimal,
) {
  override fun toString(): String =
    buildString {
    append("InstrumentMetrics(")
    append("totalInvestment=$totalInvestment, ")
    append("currentValue=$currentValue, ")
    append("profit=$profit, ")
    append("realizedProfit=$realizedProfit, ")
    append("unrealizedProfit=$unrealizedProfit, ")
    append("xirr=${"%.2f%%".format(xirr * 100)})")
  }

  companion object {
    val EMPTY =
      InstrumentMetrics(
      totalInvestment = BigDecimal.ZERO,
      currentValue = BigDecimal.ZERO,
      profit = BigDecimal.ZERO,
      realizedProfit = BigDecimal.ZERO,
      unrealizedProfit = BigDecimal.ZERO,
      xirr = 0.0,
      quantity = BigDecimal.ZERO,
    )
  }
}
