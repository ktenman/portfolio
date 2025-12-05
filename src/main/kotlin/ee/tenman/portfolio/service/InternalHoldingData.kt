package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Platform
import java.math.BigDecimal

data class InternalHoldingData(
  val ticker: String?,
  val name: String,
  val sector: String?,
  val value: BigDecimal,
  val etfSymbol: String,
  val platforms: Set<Platform>,
)
