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
    var totalQuantity = BigDecimal.ZERO
    var totalCost = BigDecimal.ZERO

    transactions.forEach { transaction ->
      when (transaction.transactionType) {
        TransactionType.BUY -> {
          val cost = transaction.price.multiply(transaction.quantity)
          totalCost = totalCost.add(cost)
          totalQuantity = totalQuantity.add(transaction.quantity)

          val averageCost =
            if (totalQuantity.compareTo(BigDecimal.ZERO) > 0) {
            totalCost.divide(totalQuantity, 10, RoundingMode.HALF_UP)
          } else {
            BigDecimal.ZERO
          }

          transaction.averageCost = averageCost
          transaction.realizedProfit = BigDecimal.ZERO

          val currentPrice = transaction.instrument.currentPrice ?: BigDecimal.ZERO
          transaction.unrealizedProfit =
            calculateProfit(
              quantity = totalQuantity,
              buyPrice = averageCost,
              currentPrice = currentPrice,
            )
        }

        TransactionType.SELL -> {
          val averageCost =
            if (totalQuantity.compareTo(BigDecimal.ZERO) > 0) {
            totalCost.divide(totalQuantity, 10, RoundingMode.HALF_UP)
          } else {
            BigDecimal.ZERO
          }

          transaction.averageCost = averageCost
          transaction.realizedProfit =
            calculateProfit(
              quantity = transaction.quantity,
              buyPrice = averageCost,
              currentPrice = transaction.price,
            )
          transaction.unrealizedProfit = BigDecimal.ZERO

          if (totalQuantity.compareTo(BigDecimal.ZERO) > 0) {
            val sellRatio = transaction.quantity.divide(totalQuantity, 10, RoundingMode.HALF_UP)
            totalCost = totalCost.multiply(BigDecimal.ONE.subtract(sellRatio))
            totalQuantity = totalQuantity.subtract(transaction.quantity)
          }
        }
      }
    }
  }

  private fun calculateProfit(
    quantity: BigDecimal,
    buyPrice: BigDecimal,
    currentPrice: BigDecimal,
  ): BigDecimal = quantity.multiply(currentPrice.subtract(buyPrice))
}
