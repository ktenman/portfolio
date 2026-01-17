package ee.tenman.portfolio.service.calculation

import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.model.FinancialConstants.CALCULATION_SCALE
import ee.tenman.portfolio.model.holding.AggregatedHoldings
import ee.tenman.portfolio.model.holding.CurrentHoldings
import ee.tenman.portfolio.model.holding.HoldingsAccumulator
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class HoldingsCalculationService {
  fun calculateCurrentHoldings(transactions: List<PortfolioTransaction>): CurrentHoldings {
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
        ?.let { totalCost.divide(it, CALCULATION_SCALE, RoundingMode.HALF_UP) }
        ?: BigDecimal.ZERO
    return CurrentHoldings(quantity, averageCost)
  }

  fun calculateAggregatedHoldings(transactions: List<PortfolioTransaction>): AggregatedHoldings {
    val result =
      transactions
        .groupBy { it.platform }
        .values
        .map { calculateCurrentHoldings(it) }
        .filter { it.quantity > BigDecimal.ZERO }
        .fold(AggregatedHoldings(BigDecimal.ZERO, BigDecimal.ZERO)) { acc, holdings ->
          AggregatedHoldings(
            totalQuantity = acc.totalQuantity.add(holdings.quantity),
            totalInvestment = acc.totalInvestment.add(holdings.quantity.multiply(holdings.averageCost)),
          )
        }
    return result
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
