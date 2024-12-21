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
    var totalQuantity = BigDecimal.ZERO
    var averageCost = BigDecimal.ZERO
    var totalCost = BigDecimal.ZERO

    transactions.forEach { transaction ->
      when (transaction.transactionType) {
        TransactionType.BUY -> {
          val newCost = transaction.price.multiply(transaction.quantity)
          totalCost = totalCost.add(newCost)
          totalQuantity = totalQuantity.add(transaction.quantity)
          averageCost = if (totalQuantity > BigDecimal.ZERO)
            totalCost.divide(totalQuantity, 10, RoundingMode.HALF_UP)
          else BigDecimal.ZERO

          transaction.averageCost = averageCost
          transaction.realizedProfit = BigDecimal.ZERO
          transaction.unrealizedProfit = calculateUnrealizedProfit(
            transaction.quantity,
            averageCost,
            transaction.instrument.currentPrice ?: BigDecimal.ZERO
          )
        }

        TransactionType.SELL -> {
          val realizedProfit = calculateRealizedProfit(
            transaction.quantity,
            transaction.price,
            averageCost
          )

          totalQuantity = totalQuantity.subtract(transaction.quantity)
          totalCost = averageCost.multiply(totalQuantity)

          transaction.averageCost = averageCost
          transaction.realizedProfit = realizedProfit
          transaction.unrealizedProfit = BigDecimal.ZERO
        }
      }
    }
  }

  private fun calculateRealizedProfit(
    quantity: BigDecimal,
    sellPrice: BigDecimal,
    averageCost: BigDecimal
  ): BigDecimal {
    return quantity.multiply(sellPrice.subtract(averageCost))
  }

  private fun calculateUnrealizedProfit(
    quantity: BigDecimal,
    averageCost: BigDecimal,
    currentPrice: BigDecimal
  ): BigDecimal {
    return quantity.multiply(currentPrice.subtract(averageCost))
  }
}
