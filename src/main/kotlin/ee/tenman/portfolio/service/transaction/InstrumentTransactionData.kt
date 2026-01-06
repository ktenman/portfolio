package ee.tenman.portfolio.service.transaction

import ee.tenman.portfolio.domain.Platform
import java.math.BigDecimal

data class InstrumentTransactionData(
  val netQuantity: BigDecimal,
  val platforms: Set<Platform>,
  val quantityByPlatform: Map<Platform, BigDecimal> = emptyMap(),
)
