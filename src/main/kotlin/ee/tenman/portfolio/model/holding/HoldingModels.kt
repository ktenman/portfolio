package ee.tenman.portfolio.model.holding

import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.GicsIndustry
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.model.FinancialConstants.CALCULATION_SCALE
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class AggregatedHoldings(
  val totalQuantity: BigDecimal,
  val totalInvestment: BigDecimal,
)

data class CurrentHoldings(
  val quantity: BigDecimal,
  val averageCost: BigDecimal,
)

data class HoldingValue(
  val totalValue: BigDecimal,
  val etfSymbols: MutableSet<String>,
  val platforms: MutableSet<Platform>,
)

data class SyntheticHoldingValue(
  val position: EtfPosition,
  val value: BigDecimal,
  val platforms: Set<Platform>,
)

data class HoldingKey(
  val holdingUuid: UUID?,
  val ticker: String?,
  val name: String,
  val sector: String?,
  val industry: String?,
  val countryCode: String?,
  val countryName: String?,
)

data class InternalHoldingData(
  val holdingUuid: UUID?,
  val ticker: String?,
  val name: String,
  val sector: String?,
  val industry: GicsIndustry?,
  val countryCode: String?,
  val countryName: String?,
  val value: BigDecimal,
  val etfSymbol: String,
  val platforms: Set<Platform>,
)

data class HoldingsAccumulator(
  val quantity: BigDecimal = BigDecimal.ZERO,
  val totalCost: BigDecimal = BigDecimal.ZERO,
) {
  fun applyBuy(tx: PortfolioTransaction): HoldingsAccumulator {
    val cost = tx.price.multiply(tx.quantity)
    return copy(quantity = quantity.add(tx.quantity), totalCost = totalCost.add(cost))
  }

  fun applySell(tx: PortfolioTransaction): HoldingsAccumulator {
    if (quantity <= BigDecimal.ZERO) return this
    val sellRatio = tx.quantity.divide(quantity, CALCULATION_SCALE, RoundingMode.HALF_UP)
    return copy(
      quantity = quantity.subtract(tx.quantity),
      totalCost = totalCost.multiply(BigDecimal.ONE.subtract(sellRatio)),
    )
  }
}
