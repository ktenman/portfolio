package ee.tenman.portfolio.service.calculation.xirr

import java.io.Serializable
import java.time.LocalDate

data class CashFlow(
  val amount: Double,
  val date: LocalDate,
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }
}
