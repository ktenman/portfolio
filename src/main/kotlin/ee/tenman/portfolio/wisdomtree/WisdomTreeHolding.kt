package ee.tenman.portfolio.wisdomtree

import java.math.BigDecimal

data class WisdomTreeHolding(
  val name: String,
  val ticker: String,
  val countryCode: String,
  val weight: BigDecimal,
)
