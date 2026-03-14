package ee.tenman.portfolio.dto

import java.math.BigDecimal

data class InstrumentComparisonDto(
  val instrumentId: Long,
  val symbol: String,
  val name: String,
  val currentPrice: BigDecimal?,
  val totalChangePercent: Double,
  val dataPoints: List<ComparisonDataPointDto>,
)
