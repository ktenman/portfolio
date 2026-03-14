package ee.tenman.portfolio.service.prediction

import java.math.BigDecimal
import java.time.LocalDate

data class PredictionContext(
  val currentValue: BigDecimal,
  val xirrAnnualReturn: BigDecimal,
  val monthlyInvestment: BigDecimal,
  val sigma: Double,
  val today: LocalDate,
)
