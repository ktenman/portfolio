package ee.tenman.portfolio.dto

import java.io.Serializable
import java.math.BigDecimal

data class ReturnPredictionDto(
  val currentValue: BigDecimal,
  val xirrAnnualReturn: BigDecimal,
  val dailyVolatility: BigDecimal,
  val dataPointCount: Int,
  val monthlyInvestment: BigDecimal,
  val predictions: List<HorizonPredictionDto>,
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }
}
