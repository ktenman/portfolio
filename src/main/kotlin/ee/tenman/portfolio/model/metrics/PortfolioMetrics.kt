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
