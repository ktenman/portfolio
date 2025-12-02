package ee.tenman.portfolio.service

import ee.tenman.portfolio.service.xirr.Transaction
import java.math.BigDecimal

data class PortfolioMetrics(
  var totalValue: BigDecimal = BigDecimal.ZERO,
  var realizedProfit: BigDecimal = BigDecimal.ZERO,
  var unrealizedProfit: BigDecimal = BigDecimal.ZERO,
  var totalProfit: BigDecimal = BigDecimal.ZERO,
  val xirrTransactions: MutableList<Transaction> = mutableListOf(),
)
