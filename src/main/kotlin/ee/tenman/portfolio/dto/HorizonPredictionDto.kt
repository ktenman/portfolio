package ee.tenman.portfolio.dto

import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDate

data class HorizonPredictionDto(
  val horizon: String,
  val horizonDays: Int,
  val targetDate: LocalDate,
  val xirrProjectedValue: BigDecimal,
  val expectedValue: BigDecimal,
  val optimisticValue: BigDecimal,
  val pessimisticValue: BigDecimal,
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }
}
