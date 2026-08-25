package ee.tenman.portfolio.dto

import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDate

data class PortfolioSummaryDto(
  val date: LocalDate,
  val totalValue: BigDecimal,
  val xirrAnnualReturn: BigDecimal,
  val realizedProfit: BigDecimal,
  val unrealizedProfit: BigDecimal,
  val totalProfit: BigDecimal,
  val earningsPerDay: BigDecimal,
  val earningsPerMonth: BigDecimal,
  val totalProfitChange24h: BigDecimal? = null,
)

data class RangeChangeDto(
  val changeAmount: BigDecimal,
  val changePercent: BigDecimal,
)

data class CalculationResult(
  var cashFlows: List<CashFlow> = mutableListOf(),
  var median: Double = 0.0,
  var average: Double = 0.0,
  var total: BigDecimal = BigDecimal.ZERO,
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }
}

data class BenchmarkPointDto(
  val date: LocalDate,
  val price: BigDecimal,
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }
}
