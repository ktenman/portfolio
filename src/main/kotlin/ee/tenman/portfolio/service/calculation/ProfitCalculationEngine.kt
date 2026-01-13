package ee.tenman.portfolio.service.calculation

import ee.tenman.portfolio.domain.InstrumentCategory
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
    val category = sortedTransactions.firstOrNull()?.instrument?.category
    val (_, currentQuantity) = processTransactions(sortedTransactions)
    val effectivePrice = determineEffectivePrice(currentPrice, sortedTransactions)
    distributeProfits(sortedTransactions, currentQuantity, effectivePrice, category)
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
    if (state.currentQuantity.compareTo(BigDecimal.ZERO) <= 0) return state
    val effectiveQuantity = transaction.quantity.min(state.currentQuantity)
    val sellRatio = effectiveQuantity.divide(state.currentQuantity, CALCULATION_SCALE, RoundingMode.HALF_UP)
    return TransactionState(
      state.totalCost.multiply(BigDecimal.ONE.subtract(sellRatio)),
      state.currentQuantity.subtract(effectiveQuantity),
    )
  }

  private fun determineEffectivePrice(
    passedPrice: BigDecimal,
    transactions: List<PortfolioTransaction>,
  ): BigDecimal =
    passedPrice.takeIf { it.compareTo(BigDecimal.ZERO) > 0 }
      ?: transactions.firstOrNull()?.instrument?.currentPrice
      ?: BigDecimal.ZERO

  private fun distributeProfits(
    transactions: List<PortfolioTransaction>,
    currentQuantity: BigDecimal,
    currentPrice: BigDecimal,
    category: String?,
  ) {
    val buyTransactions = transactions.filter { it.transactionType == TransactionType.BUY }
    if (currentQuantity.compareTo(BigDecimal.ZERO) <= 0) {
      buyTransactions.forEach { it.setZeroUnrealizedMetrics() }
      return
    }
    when (category) {
      InstrumentCategory.CASH.name -> {
        val totalSold = transactions.filter { it.transactionType == TransactionType.SELL }.sumOf { it.quantity }
        distributeToBuyTransactionsFifo(buyTransactions, totalSold, currentQuantity, currentPrice)
      }
      else -> distributeToBuyTransactions(buyTransactions, currentQuantity, currentPrice)
    }
  }

  private fun distributeToBuyTransactions(
    buyTransactions: List<PortfolioTransaction>,
    currentQuantity: BigDecimal,
    currentPrice: BigDecimal,
  ) {
    val totalBuyQuantity = buyTransactions.sumOf { it.quantity }
    if (totalBuyQuantity.compareTo(BigDecimal.ZERO) <= 0) return
    buyTransactions.forEach { buyTx ->
      val proportionalQuantity =
        buyTx.quantity
          .multiply(currentQuantity)
          .divide(totalBuyQuantity, CALCULATION_SCALE, RoundingMode.HALF_UP)
      buyTx.remainingQuantity = proportionalQuantity
      buyTx.averageCost = buyTx.price
      buyTx.unrealizedProfit = currentPrice
        .takeIf { it.compareTo(BigDecimal.ZERO) > 0 }
        ?.let { proportionalQuantity.multiply(it.subtract(buyTx.price)) }
        ?: BigDecimal.ZERO
    }
  }

  private fun distributeToBuyTransactionsFifo(
    buyTransactions: List<PortfolioTransaction>,
    totalSold: BigDecimal,
    currentQuantity: BigDecimal,
    currentPrice: BigDecimal,
  ) {
    buyTransactions.fold(Pair(totalSold, currentQuantity)) { (soldToConsume, remainingToAllocate), buyTx ->
      val consumedFromThisBuy = soldToConsume.min(buyTx.quantity)
      val newSoldToConsume = soldToConsume.subtract(consumedFromThisBuy).max(BigDecimal.ZERO)
      val availableFromThisBuy = buyTx.quantity.subtract(consumedFromThisBuy)
      val allocated = availableFromThisBuy.min(remainingToAllocate)
      buyTx.remainingQuantity = allocated
      buyTx.averageCost = buyTx.price
      buyTx.unrealizedProfit = currentPrice
        .takeIf { it.compareTo(BigDecimal.ZERO) > 0 }
        ?.let { allocated.multiply(it.subtract(buyTx.price)) }
        ?: BigDecimal.ZERO
      Pair(newSoldToConsume, remainingToAllocate.subtract(allocated))
    }
  }

  fun calculateAverageCost(
    totalCost: BigDecimal,
    quantity: BigDecimal,
  ): BigDecimal =
    quantity
      .takeIf { it.compareTo(BigDecimal.ZERO) > 0 }
      ?.let { totalCost.divide(it, CALCULATION_SCALE, RoundingMode.HALF_UP) }
      ?: BigDecimal.ZERO

  fun calculateUnrealizedProfit(
    quantity: BigDecimal,
    price: BigDecimal,
    avgCost: BigDecimal,
  ): BigDecimal =
    quantity
      .takeIf { it.compareTo(BigDecimal.ZERO) > 0 && price.compareTo(BigDecimal.ZERO) > 0 }
      ?.multiply(price.subtract(avgCost))
      ?: BigDecimal.ZERO

  private fun PortfolioTransaction.setZeroUnrealizedMetrics() {
    this.remainingQuantity = BigDecimal.ZERO
    this.unrealizedProfit = BigDecimal.ZERO
    this.averageCost = this.price
  }
}
