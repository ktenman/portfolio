package ee.tenman.portfolio.model.holding

import ee.tenman.portfolio.domain.PortfolioTransaction
import java.math.BigDecimal
import java.math.RoundingMode

data class HoldingsAccumulator(
  val quantity: BigDecimal = BigDecimal.ZERO,
  val totalCost: BigDecimal = BigDecimal.ZERO,
) {
  fun applyBuy(tx: PortfolioTransaction): HoldingsAccumulator {
    val cost = tx.price.multiply(tx.quantity).add(tx.commission)
    return copy(quantity = quantity.add(tx.quantity), totalCost = totalCost.add(cost))
  }

  fun applySell(tx: PortfolioTransaction): HoldingsAccumulator {
    if (quantity <= BigDecimal.ZERO) return this
    val sellRatio = tx.quantity.divide(quantity, 10, RoundingMode.HALF_UP)
    return copy(
      quantity = quantity.subtract(tx.quantity),
      totalCost = totalCost.multiply(BigDecimal.ONE.subtract(sellRatio)),
    )
  }
}
