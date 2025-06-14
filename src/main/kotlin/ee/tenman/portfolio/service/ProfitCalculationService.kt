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
    var buyPrice = BigDecimal.ZERO

    transactions.forEach { transaction ->
      when (transaction.transactionType) {
        TransactionType.BUY -> {
          buyPrice = transaction.price
          transaction.averageCost = buyPrice
          transaction.realizedProfit = BigDecimal.ZERO

          val currentPrice = transaction.instrument.currentPrice ?: BigDecimal.ZERO
          transaction.unrealizedProfit =
            calculateProfit(
              quantity = transaction.quantity,
              buyPrice = buyPrice,
              currentPrice = currentPrice,
            )
        }

        TransactionType.SELL -> {
          transaction.averageCost = buyPrice
          transaction.realizedProfit =
            calculateProfit(
              quantity = transaction.quantity,
              buyPrice = buyPrice,
              currentPrice = transaction.price,
            )
          transaction.unrealizedProfit = BigDecimal.ZERO
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
