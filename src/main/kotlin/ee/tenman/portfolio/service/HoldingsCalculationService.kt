package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.model.holding.HoldingsAccumulator
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class HoldingsCalculationService {
  fun calculateCurrentHoldings(transactions: List<PortfolioTransaction>): Pair<BigDecimal, BigDecimal> {
    val (quantity, totalCost) =
      transactions
        .sortedWith(compareBy({ it.transactionDate }, { it.id }))
        .fold(HoldingsAccumulator()) { acc, tx ->
          when (tx.transactionType) {
            TransactionType.BUY -> acc.applyBuy(tx)
            TransactionType.SELL -> acc.applySell(tx)
          }
        }
    val averageCost =
      quantity
        .takeIf { it > BigDecimal.ZERO }
        ?.let { totalCost.divide(it, 10, RoundingMode.HALF_UP) }
        ?: BigDecimal.ZERO
    return quantity to averageCost
  }

  fun calculateAggregatedHoldings(transactions: List<PortfolioTransaction>): Pair<BigDecimal, BigDecimal> =
    transactions
      .groupBy { it.platform }
      .values
      .map { calculateCurrentHoldings(it) }
      .filter { (quantity, _) -> quantity > BigDecimal.ZERO }
      .fold(BigDecimal.ZERO to BigDecimal.ZERO) { (totalHoldings, totalInvestment), (quantity, avgCost) ->
        totalHoldings.add(quantity) to totalInvestment.add(quantity.multiply(avgCost))
      }

  fun calculateNetQuantity(transactions: List<PortfolioTransaction>): BigDecimal =
    transactions.fold(BigDecimal.ZERO) { acc, tx ->
      when (tx.transactionType) {
        TransactionType.BUY -> acc.add(tx.quantity)
        TransactionType.SELL -> acc.subtract(tx.quantity)
      }
    }

  fun calculateCurrentValue(
    holdings: BigDecimal,
    currentPrice: BigDecimal,
  ): BigDecimal = holdings.multiply(currentPrice)

  fun calculateProfit(
    holdings: BigDecimal,
    averageCost: BigDecimal,
    currentPrice: BigDecimal,
  ): BigDecimal {
    val currentValue = holdings.multiply(currentPrice)
    val investment = holdings.multiply(averageCost)
    return currentValue.subtract(investment)
  }
}
