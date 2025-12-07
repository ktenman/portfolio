package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.model.TransactionState
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class TransactionProfitService(
  private val instrumentRepository: InstrumentRepository,
  private val portfolioTransactionRepository: PortfolioTransactionRepository,
) {
  @Transactional
  fun recalculateProfitsForInstrument(instrumentId: Long) {
    val instrument = instrumentRepository.findById(instrumentId).orElse(null) ?: return
    val transactions = portfolioTransactionRepository.findAllByInstrumentId(instrumentId)
    if (transactions.isEmpty()) return
    transactions.forEach { it.instrument = instrument }
    transactions
      .groupBy { it.platform }
      .forEach { (_, platformTransactions) ->
        calculateProfitsForPlatform(platformTransactions.sortedWith(compareBy({ it.transactionDate }, { it.id })))
      }
    portfolioTransactionRepository.saveAll(transactions)
  }

  private fun calculateProfitsForPlatform(transactions: List<PortfolioTransaction>) {
    val (totalCost, currentQuantity) = processTransactions(transactions)
    val currentPrice = transactions.firstOrNull()?.instrument?.currentPrice ?: BigDecimal.ZERO
    val averageCost = calculateAverageCost(totalCost, currentQuantity)
    val totalUnrealizedProfit = calculateUnrealizedProfit(currentQuantity, currentPrice, averageCost)
    val buyTransactions = transactions.filter { it.transactionType == TransactionType.BUY }
    distributeProfitsToBuyTransactions(buyTransactions, currentQuantity, averageCost, totalUnrealizedProfit)
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
    return TransactionState(state.totalCost.add(cost), state.currentQuantity.add(transaction.quantity))
  }

  private fun processSellTransaction(
    transaction: PortfolioTransaction,
    state: TransactionState,
  ): TransactionState {
    if (state.currentQuantity.compareTo(BigDecimal.ZERO) <= 0) {
      transaction.averageCost = BigDecimal.ZERO
      transaction.realizedProfit = BigDecimal.ZERO
      transaction.unrealizedProfit = BigDecimal.ZERO
      transaction.remainingQuantity = BigDecimal.ZERO
      return state
    }
    val actualSellQuantity = transaction.quantity.min(state.currentQuantity)
    val averageCost = calculateAverageCost(state.totalCost, state.currentQuantity)
    transaction.averageCost = averageCost
    transaction.realizedProfit = actualSellQuantity.multiply(transaction.price.subtract(averageCost)).subtract(transaction.commission)
    transaction.unrealizedProfit = BigDecimal.ZERO
    transaction.remainingQuantity = BigDecimal.ZERO
    val sellRatio = actualSellQuantity.divide(state.currentQuantity, 10, RoundingMode.HALF_UP)
    return TransactionState(
      state.totalCost.multiply(BigDecimal.ONE.subtract(sellRatio)),
      state.currentQuantity.subtract(actualSellQuantity),
    )
  }

  private fun calculateAverageCost(
    totalCost: BigDecimal,
    quantity: BigDecimal,
  ): BigDecimal = if (quantity.compareTo(BigDecimal.ZERO) > 0) totalCost.divide(quantity, 10, RoundingMode.HALF_UP) else BigDecimal.ZERO

  private fun calculateUnrealizedProfit(
    quantity: BigDecimal,
    price: BigDecimal,
    avgCost: BigDecimal,
  ): BigDecimal =
    if (quantity.compareTo(BigDecimal.ZERO) > 0 &&
    price.compareTo(BigDecimal.ZERO) > 0
    ) {
      quantity.multiply(price.subtract(avgCost))
    } else {
      BigDecimal.ZERO
    }

  private fun distributeProfitsToBuyTransactions(
    buyTransactions: List<PortfolioTransaction>,
    currentQuantity: BigDecimal,
    averageCost: BigDecimal,
    totalUnrealizedProfit: BigDecimal,
  ) {
    if (currentQuantity.compareTo(BigDecimal.ZERO) <= 0) {
      buyTransactions.forEach {
        it.remainingQuantity = BigDecimal.ZERO
        it.unrealizedProfit = BigDecimal.ZERO
        it.averageCost = it.price
      }
      return
    }
    val totalBuyQuantity = buyTransactions.sumOf { it.quantity }
    if (totalBuyQuantity.compareTo(BigDecimal.ZERO) <= 0) return
    buyTransactions.forEach { buyTx ->
      val proportionalQuantity =
        buyTx.quantity
          .multiply(currentQuantity)
          .divide(totalBuyQuantity, 10, RoundingMode.HALF_UP)
      buyTx.remainingQuantity = proportionalQuantity
      buyTx.averageCost = averageCost
      buyTx.unrealizedProfit =
        totalUnrealizedProfit
          .multiply(proportionalQuantity)
          .divide(currentQuantity, 10, RoundingMode.HALF_UP)
    }
  }
}
