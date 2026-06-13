package ee.tenman.portfolio.blackrock

import java.math.BigDecimal

data class BlackRockHolding(
  val ticker: String?,
  val name: String,
  val sector: String?,
  val weight: BigDecimal,
)
