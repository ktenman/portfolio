package ee.tenman.portfolio.dto

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
)
