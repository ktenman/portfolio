package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Platform
import java.math.BigDecimal

data class HoldingValue(
  val totalValue: BigDecimal,
  val etfSymbols: MutableSet<String>,
  val platforms: MutableSet<Platform>,
)
