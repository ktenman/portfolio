package ee.tenman.portfolio.model.holding

import ee.tenman.portfolio.domain.Platform
import java.math.BigDecimal

data class HoldingValue(
  val totalValue: BigDecimal,
  val etfSymbols: MutableSet<String>,
  val platforms: MutableSet<Platform>,
)
