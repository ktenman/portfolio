package ee.tenman.portfolio.lightyear

import ee.tenman.portfolio.common.DailyPriceData
import java.math.BigDecimal

data class LightyearChartDataPoint(
  val timestamp: String,
  val open: BigDecimal,
  val close: BigDecimal,
  val high: BigDecimal,
  val low: BigDecimal,
  val volume: Long,
)

data class LightyearDailyPriceData(
  override val open: BigDecimal,
  override val high: BigDecimal,
  override val low: BigDecimal,
  override val close: BigDecimal,
  override val volume: Long,
) : DailyPriceData
