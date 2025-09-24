package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import org.slf4j.LoggerFactory
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class ProfitCalculationService {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  @Retryable(
    value = [ObjectOptimisticLockingFailureException::class],
    maxAttempts = 5,
    backoff = Backoff(delay = 100),
  )
  fun calculateProfits(transactions: List<PortfolioTransaction>) {
    try {
      transactions
        .groupBy { it.platform to it.instrument.id }
        .forEach { (_, platformTransactions) ->
          calculateProfitsForPlatform(platformTransactions.sortedBy { it.transactionDate })
        }
    } catch (e: ObjectOptimisticLockingFailureException) {
      log.warn("Optimistic locking failure while calculating profits. Will retry.", e)
      throw e
    }
  }

  private fun calculateProfitsForPlatform(transactions: List<PortfolioTransaction>) {
    val sortedTransactions = transactions.sortedBy { it.transactionDate }

    var currentQuantity = BigDecimal.ZERO
    var totalCost = BigDecimal.ZERO

    sortedTransactions.forEach { transaction ->
      when (transaction.transactionType) {
        TransactionType.BUY -> {
          val cost = transaction.price.multiply(transaction.quantity).add(transaction.commission)
          totalCost = totalCost.add(cost)
          currentQuantity = currentQuantity.add(transaction.quantity)
          transaction.realizedProfit = BigDecimal.ZERO
        }
        TransactionType.SELL -> {
          val averageCost =
            if (currentQuantity > BigDecimal.ZERO) {
            totalCost.divide(currentQuantity, 10, RoundingMode.HALF_UP)
          } else {
            BigDecimal.ZERO
          }

          transaction.averageCost = averageCost
          val grossProfit =
            calculateProfit(
            quantity = transaction.quantity,
            buyPrice = averageCost,
            currentPrice = transaction.price,
          )
          transaction.realizedProfit = grossProfit.subtract(transaction.commission)
          transaction.unrealizedProfit = BigDecimal.ZERO
          transaction.remainingQuantity = BigDecimal.ZERO

          if (currentQuantity > BigDecimal.ZERO) {
            val sellRatio = transaction.quantity.divide(currentQuantity, 10, RoundingMode.HALF_UP)
            totalCost = totalCost.multiply(BigDecimal.ONE.subtract(sellRatio))
            currentQuantity = currentQuantity.subtract(transaction.quantity)
          }
        }
      }
    }

    val currentPrice =
      sortedTransactions
      .firstOrNull()
        ?.instrument
        ?.currentPrice ?: BigDecimal.ZERO

    val averageCost =
      if (currentQuantity > BigDecimal.ZERO) {
      totalCost.divide(currentQuantity, 10, RoundingMode.HALF_UP)
    } else {
      BigDecimal.ZERO
    }

    val totalUnrealizedProfit =
      if (currentQuantity > BigDecimal.ZERO && currentPrice > BigDecimal.ZERO) {
      calculateProfit(
        quantity = currentQuantity,
        buyPrice = averageCost,
        currentPrice = currentPrice,
      )
    } else {
      BigDecimal.ZERO
    }

    sortedTransactions.filter { it.transactionType == TransactionType.BUY }.forEach { buyTx ->
      if (currentQuantity > BigDecimal.ZERO) {
        val proportionalQuantity =
          buyTx.quantity
            .multiply(currentQuantity)
          .divide(sortedTransactions.filter { it.transactionType == TransactionType.BUY }.sumOf { it.quantity }, 10, RoundingMode.HALF_UP)

        buyTx.remainingQuantity = proportionalQuantity
        buyTx.averageCost = averageCost

        val proportionalProfit =
          if (currentQuantity > BigDecimal.ZERO) {
          totalUnrealizedProfit
            .multiply(buyTx.remainingQuantity)
            .divide(currentQuantity, 10, RoundingMode.HALF_UP)
        } else {
          BigDecimal.ZERO
        }

        buyTx.unrealizedProfit = proportionalProfit
      } else {
        buyTx.remainingQuantity = BigDecimal.ZERO
        buyTx.unrealizedProfit = BigDecimal.ZERO
        buyTx.averageCost = buyTx.price
      }
    }
  }

  private fun calculateProfit(
    quantity: BigDecimal,
    buyPrice: BigDecimal,
    currentPrice: BigDecimal,
  ): BigDecimal = quantity.multiply(currentPrice.subtract(buyPrice))
}
