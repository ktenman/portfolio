package ee.tenman.portfolio.model.metrics

import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import java.math.BigDecimal

data class PortfolioMetrics(
  var totalValue: BigDecimal = BigDecimal.ZERO,
  var realizedProfit: BigDecimal = BigDecimal.ZERO,
  var unrealizedProfit: BigDecimal = BigDecimal.ZERO,
  var totalProfit: BigDecimal = BigDecimal.ZERO,
  val xirrCashFlows: MutableList<CashFlow> = mutableListOf(),
)

data class InstrumentMetrics(
  val totalInvestment: BigDecimal,
  val currentValue: BigDecimal,
  val profit: BigDecimal,
  val realizedProfit: BigDecimal,
  val unrealizedProfit: BigDecimal,
  val xirr: Double?,
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
    append("xirr=${xirr?.let { "%.2f%%".format(it * 100) } ?: "N/A"})")
  }

  companion object {
    val EMPTY =
      InstrumentMetrics(
      totalInvestment = BigDecimal.ZERO,
      currentValue = BigDecimal.ZERO,
      profit = BigDecimal.ZERO,
      realizedProfit = BigDecimal.ZERO,
      unrealizedProfit = BigDecimal.ZERO,
      xirr = null,
      quantity = BigDecimal.ZERO,
    )
  }
}
