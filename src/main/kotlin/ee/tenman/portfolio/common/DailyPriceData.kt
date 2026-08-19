package ee.tenman.portfolio.common

import java.math.BigDecimal

interface DailyPriceData {
  val open: BigDecimal
  val high: BigDecimal
  val low: BigDecimal
  val close: BigDecimal
  val volume: Long
}

data class DailyPriceDataImpl(
  override val open: BigDecimal,
  override val high: BigDecimal,
  override val low: BigDecimal,
  override val close: BigDecimal,
  override val volume: Long,
) : DailyPriceData
