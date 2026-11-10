package ee.tenman.portfolio.domain

import java.math.BigDecimal

data class OpenRouterMonthlySpend(
  val month: String,
  val purpose: String,
  val model: String,
  val cost: BigDecimal,
)
