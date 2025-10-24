package ee.tenman.portfolio.dto

import java.math.BigDecimal

data class HoldingData(
  val name: String,
  val ticker: String?,
  val sector: String?,
  val weight: BigDecimal,
  val marketCap: String?,
  val price: String?,
  val dayChange: String?,
  val rank: Int,
)
