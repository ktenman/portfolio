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
    backoff = Backoff(delay = 100)
  )
  fun calculateProfits(transactions: List<PortfolioTransaction>) {
    try {
      transactions.groupBy { it.platform to it.instrument.id }
        .forEach { (_, platformTransactions) ->
          calculateProfitsForPlatform(platformTransactions.sortedBy { it.transactionDate })
        }
    } catch (e: ObjectOptimisticLockingFailureException) {
      log.warn("Optimistic locking failure while calculating profits. Will retry.", e)
      throw e
    }
  }

  private fun calculateProfitsForPlatform(transactions: List<PortfolioTransaction>) {
    var runningQuantity = BigDecimal.ZERO
    var runningCost = BigDecimal.ZERO

    transactions.forEach { transaction ->
      when (transaction.transactionType) {
        TransactionType.BUY -> {
          runningCost = runningCost.add(transaction.price.multiply(transaction.quantity))
          runningQuantity = runningQuantity.add(transaction.quantity)

          val currentPrice = transaction.instrument.currentPrice ?: BigDecimal.ZERO
          val profit = calculateProfitForPrice(
            quantity = transaction.quantity,
            buyPrice = transaction.price,
            currentPrice = currentPrice
          )

          transaction.averageCost = if (runningQuantity > BigDecimal.ZERO) {
            runningCost.divide(runningQuantity, 10, RoundingMode.HALF_UP)
          } else transaction.price

          transaction.realizedProfit = BigDecimal.ZERO
          transaction.unrealizedProfit = profit
        }
        TransactionType.SELL -> {
          val previousAverageCost = if (runningQuantity > BigDecimal.ZERO) {
            runningCost.divide(runningQuantity, 10, RoundingMode.HALF_UP)
          } else BigDecimal.ZERO

          val profit = calculateProfitForPrice(
            quantity = transaction.quantity,
            buyPrice = previousAverageCost,
            currentPrice = transaction.price
          )

          runningQuantity = runningQuantity.subtract(transaction.quantity)
          if (runningQuantity > BigDecimal.ZERO) {
            runningCost = previousAverageCost.multiply(runningQuantity)
          } else {
            runningCost = BigDecimal.ZERO
          }

          transaction.averageCost = previousAverageCost
          transaction.realizedProfit = profit
          transaction.unrealizedProfit = BigDecimal.ZERO
        }
      }
    }
  }

  private fun calculateProfitForPrice(
    quantity: BigDecimal,
    buyPrice: BigDecimal,
    currentPrice: BigDecimal
  ): BigDecimal {
    return quantity.multiply(currentPrice.subtract(buyPrice))
  }
}
