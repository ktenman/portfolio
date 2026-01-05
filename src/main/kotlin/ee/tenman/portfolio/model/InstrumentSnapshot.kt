package ee.tenman.portfolio.model

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import java.math.BigDecimal

data class InstrumentSnapshot(
  val instrument: Instrument,
  val totalInvestment: BigDecimal = BigDecimal.ZERO,
  val currentValue: BigDecimal = BigDecimal.ZERO,
  val profit: BigDecimal = BigDecimal.ZERO,
  val realizedProfit: BigDecimal = BigDecimal.ZERO,
  val unrealizedProfit: BigDecimal = BigDecimal.ZERO,
  val xirr: Double? = null,
  val quantity: BigDecimal = BigDecimal.ZERO,
  val platforms: Set<Platform> = emptySet(),
  val priceChangeAmount: BigDecimal? = null,
  val priceChangePercent: Double? = null,
)
