package ee.tenman.portfolio.service.prediction

import java.math.BigDecimal
import java.time.LocalDate

data class PredictionContext(
  val currentValue: BigDecimal,
  val xirrAnnualReturn: BigDecimal,
  val monthlyInvestment: BigDecimal,
  val stats: VolatilityStats,
  val today: LocalDate,
)
