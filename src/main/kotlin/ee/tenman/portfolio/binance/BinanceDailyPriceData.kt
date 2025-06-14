package ee.tenman.portfolio.binance

import ee.tenman.portfolio.common.DailyPriceData
import java.math.BigDecimal

data class BinanceDailyPriceData(
  override val open: BigDecimal,
  override val high: BigDecimal,
  override val low: BigDecimal,
  override val close: BigDecimal,
  override val volume: Long,
) : DailyPriceData
