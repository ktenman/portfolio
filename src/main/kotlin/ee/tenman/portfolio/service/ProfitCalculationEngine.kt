package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.model.FinancialConstants.CALCULATION_SCALE
import ee.tenman.portfolio.model.TransactionState
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class ProfitCalculationEngine {
  fun calculateProfitsForPlatform(
    transactions: List<PortfolioTransaction>,
    currentPrice: BigDecimal,
  ) {
    val sortedTransactions = transactions.sortedWith(compareBy({ it.transactionDate }, { it.id }))
    val (_, currentQuantity) = processTransactions(sortedTransactions)
    val effectivePrice = determineEffectivePrice(currentPrice, sortedTransactions)
    distributeProfits(sortedTransactions, currentQuantity, effectivePrice)
  }

  private fun processTransactions(transactions: List<PortfolioTransaction>): TransactionState =
    transactions.fold(TransactionState(BigDecimal.ZERO, BigDecimal.ZERO)) { state, transaction ->
      when (transaction.transactionType) {
        TransactionType.BUY -> processBuyTransaction(transaction, state)
        TransactionType.SELL -> processSellTransaction(transaction, state)
      }
    }

  private fun processBuyTransaction(
    transaction: PortfolioTransaction,
    state: TransactionState,
  ): TransactionState {
    val cost = transaction.price.multiply(transaction.quantity).add(transaction.commission)
    transaction.realizedProfit = BigDecimal.ZERO
    return TransactionState(
      state.totalCost.add(cost),
      state.currentQuantity.add(transaction.quantity),
    )
  }

  private fun processSellTransaction(
    transaction: PortfolioTransaction,
    state: TransactionState,
  ): TransactionState {
    val averageCost = calculateAverageCost(state.totalCost, state.currentQuantity)
    transaction.averageCost = averageCost
    transaction.realizedProfit =
      transaction.quantity
        .multiply(transaction.price.subtract(averageCost))
        .subtract(transaction.commission)
    transaction.unrealizedProfit = BigDecimal.ZERO
    transaction.remainingQuantity = BigDecimal.ZERO
    if (state.currentQuantity <= BigDecimal.ZERO) return state
    val sellRatio = transaction.quantity.divide(state.currentQuantity, CALCULATION_SCALE, RoundingMode.HALF_UP)
    return TransactionState(
      state.totalCost.multiply(BigDecimal.ONE.subtract(sellRatio)),
      state.currentQuantity.subtract(transaction.quantity),
    )
  }

  private fun determineEffectivePrice(
    passedPrice: BigDecimal,
    transactions: List<PortfolioTransaction>,
  ): BigDecimal =
    passedPrice.takeIf { it > BigDecimal.ZERO }
      ?: transactions.firstOrNull()?.instrument?.currentPrice
      ?: BigDecimal.ZERO

  private fun distributeProfits(
    transactions: List<PortfolioTransaction>,
    currentQuantity: BigDecimal,
    currentPrice: BigDecimal,
  ) {
    val buyTransactions = transactions.filter { it.transactionType == TransactionType.BUY }
    if (currentQuantity <= BigDecimal.ZERO) {
      buyTransactions.forEach { it.setZeroUnrealizedMetrics() }
      return
    }
    distributeToBuyTransactions(buyTransactions, currentQuantity, currentPrice)
  }

  private fun distributeToBuyTransactions(
    buyTransactions: List<PortfolioTransaction>,
    currentQuantity: BigDecimal,
    currentPrice: BigDecimal,
  ) {
    val totalBuyQuantity = buyTransactions.sumOf { it.quantity }
    if (totalBuyQuantity <= BigDecimal.ZERO) return
    buyTransactions.forEach { buyTx ->
      val proportionalQuantity =
        buyTx.quantity
          .multiply(currentQuantity)
          .divide(totalBuyQuantity, CALCULATION_SCALE, RoundingMode.HALF_UP)
      buyTx.remainingQuantity = proportionalQuantity
      buyTx.averageCost = buyTx.price
      buyTx.unrealizedProfit = currentPrice
        .takeIf { it > BigDecimal.ZERO }
        ?.let { proportionalQuantity.multiply(it.subtract(buyTx.price)) }
        ?: BigDecimal.ZERO
    }
  }

  fun calculateAverageCost(
    totalCost: BigDecimal,
    quantity: BigDecimal,
  ): BigDecimal =
    quantity
      .takeIf { it > BigDecimal.ZERO }
      ?.let { totalCost.divide(it, CALCULATION_SCALE, RoundingMode.HALF_UP) }
      ?: BigDecimal.ZERO

  fun calculateUnrealizedProfit(
    quantity: BigDecimal,
    price: BigDecimal,
    avgCost: BigDecimal,
  ): BigDecimal =
    quantity
      .takeIf { it > BigDecimal.ZERO && price > BigDecimal.ZERO }
      ?.multiply(price.subtract(avgCost))
      ?: BigDecimal.ZERO

  private fun PortfolioTransaction.setZeroUnrealizedMetrics() {
    this.remainingQuantity = BigDecimal.ZERO
    this.unrealizedProfit = BigDecimal.ZERO
    this.averageCost = this.price
  }
}
