package ee.tenman.portfolio.model.holding

import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Platform
import java.math.BigDecimal

data class SyntheticHoldingValue(
  val position: EtfPosition,
  val value: BigDecimal,
  val platforms: Set<Platform>,
)
