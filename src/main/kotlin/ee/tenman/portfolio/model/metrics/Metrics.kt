package ee.tenman.portfolio.model.metrics

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.model.holding.AggregatedHoldings
import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import ee.tenman.portfolio.service.calculation.xirr.XirrSchedule
import java.math.BigDecimal
import java.time.LocalDate

data class PortfolioMetrics(
  var totalValue: BigDecimal = BigDecimal.ZERO,
  var realizedProfit: BigDecimal = BigDecimal.ZERO,
  var unrealizedProfit: BigDecimal = BigDecimal.ZERO,
  var totalProfit: BigDecimal = BigDecimal.ZERO,
  val xirrCashFlows: MutableList<CashFlow> = mutableListOf(),
)

data class PreparedHolding(
  val instrument: Instrument,
  val transactions: List<PortfolioTransaction>,
  val aggregated: AggregatedHoldings?,
  val realizedProfit: BigDecimal,
  val cashFlows: List<CashFlow>,
) {
  val held: Boolean get() = aggregated?.let { it.totalQuantity > BigDecimal.ZERO } ?: true
}

data class PreparedDay(
  val date: LocalDate,
  val baselineProfit: BigDecimal,
  val trailingDays: Long,
  val holdings: List<PreparedHolding>,
) {
  private var schedule: XirrSchedule? = null

  fun xirrSchedule(cashFlows: List<CashFlow>): XirrSchedule =
    schedule?.takeIf { it.fits(cashFlows) } ?: XirrSchedule(cashFlows).also { schedule = it }
}

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
