package ee.tenman.portfolio.service.calculation

import ee.tenman.portfolio.domain.Platform
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
  fun calculateCurrentHoldings(transactions: List<PortfolioTransaction>): CurrentHoldings =
    toCurrentHoldings(
      transactions
        .sortedWith(compareBy({ it.transactionDate }, { it.id }))
        .fold(HoldingsAccumulator(), ::accumulate),
    )

  fun calculateAggregatedHoldings(transactions: List<PortfolioTransaction>): AggregatedHoldings =
    aggregate(transactions.groupBy { it.platform }.values.map { calculateCurrentHoldings(it) })

  fun calculateAggregatedHoldingPrefixes(sortedTransactions: List<PortfolioTransaction>): List<AggregatedHoldings> {
    val platforms = mutableMapOf<Platform, HoldingsAccumulator>()
    return sortedTransactions.map { tx ->
      platforms[tx.platform] = accumulate(platforms[tx.platform] ?: HoldingsAccumulator(), tx)
      aggregate(platforms.values.map(::toCurrentHoldings))
    }
  }

  private fun accumulate(
    accumulator: HoldingsAccumulator,
    tx: PortfolioTransaction,
  ): HoldingsAccumulator =
    when (tx.transactionType) {
      TransactionType.BUY -> accumulator.applyBuy(tx)
      TransactionType.SELL -> accumulator.applySell(tx)
    }

  private fun toCurrentHoldings(accumulator: HoldingsAccumulator): CurrentHoldings {
    val averageCost =
      accumulator.quantity
        .takeIf { it > BigDecimal.ZERO }
        ?.let { accumulator.totalCost.divide(it, CALCULATION_SCALE, RoundingMode.HALF_UP) }
        ?: BigDecimal.ZERO
    return CurrentHoldings(accumulator.quantity, averageCost)
  }

  private fun aggregate(holdings: List<CurrentHoldings>): AggregatedHoldings =
    holdings
      .filter { it.quantity > BigDecimal.ZERO }
      .fold(AggregatedHoldings(BigDecimal.ZERO, BigDecimal.ZERO)) { acc, holding ->
        AggregatedHoldings(
          totalQuantity = acc.totalQuantity.add(holding.quantity),
          totalInvestment = acc.totalInvestment.add(holding.quantity.multiply(holding.averageCost)),
        )
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
