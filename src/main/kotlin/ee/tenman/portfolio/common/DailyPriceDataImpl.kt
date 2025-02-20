package ee.tenman.portfolio.common

import java.math.BigDecimal

data class DailyPriceDataImpl(
  override val open: BigDecimal,
  override val high: BigDecimal,
  override val low: BigDecimal,
  override val close: BigDecimal,
  override val volume: Long
) : DailyPriceData
