package ee.tenman.portfolio.common

import java.math.BigDecimal

interface DayData {
  val open: BigDecimal
  val high: BigDecimal
  val low: BigDecimal
  val close: BigDecimal
  val volume: Long
}
