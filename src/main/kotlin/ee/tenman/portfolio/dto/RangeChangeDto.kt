package ee.tenman.portfolio.dto

import java.math.BigDecimal

data class RangeChangeDto(
  val changeAmount: BigDecimal,
  val changePercent: BigDecimal,
)
