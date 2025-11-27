package ee.tenman.portfolio.dto

import ee.tenman.portfolio.service.xirr.Transaction
import java.io.Serializable

data class InstrumentRollingXirrDto(
  val instrumentId: Long,
  val symbol: String,
  val name: String,
  val rollingXirrs: List<Transaction>,
  val medianXirr: Double,
  val portfolioWeight: Double,
  val weightedXirr: Double,
  val currentValue: Double,
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }
}

data class PortfolioRollingXirrDto(
  val instruments: List<InstrumentRollingXirrDto>,
  val portfolioAverageXirr: Double,
  val portfolioWeightedXirr: Double,
  val totalPortfolioValue: Double,
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }
}
