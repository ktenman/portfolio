package ee.tenman.portfolio.dto

import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import java.io.Serializable
import java.math.BigDecimal

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
