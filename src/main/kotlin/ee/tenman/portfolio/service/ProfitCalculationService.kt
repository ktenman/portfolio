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
    backoff = Backoff(delay = 100)
  )
  fun calculateProfits(transactions: List<PortfolioTransaction>) {
    try {
      transactions.forEach { transaction ->
        calculateProfit(transaction)
      }
    } catch (e: ObjectOptimisticLockingFailureException) {
      log.warn("Optimistic locking failure while calculating profits. Will retry.", e)
      throw e
    }
  }

  private fun calculateProfit(transaction: PortfolioTransaction) {
    when (transaction.transactionType) {
      TransactionType.BUY -> {
        val currentPrice = transaction.instrument.currentPrice ?: BigDecimal.ZERO
        val profit = calculateProfitForPrice(
          quantity = transaction.quantity,
          buyPrice = transaction.price,
          currentPrice = currentPrice
        )

        transaction.realizedProfit = BigDecimal.ZERO
        transaction.unrealizedProfit = profit
        transaction.averageCost = transaction.price
      }
      TransactionType.SELL -> {
        val profit = calculateProfitForPrice(
          quantity = transaction.quantity,
          buyPrice = transaction.averageCost ?: BigDecimal.ZERO,
          currentPrice = transaction.price
        )

        transaction.realizedProfit = profit
        transaction.unrealizedProfit = BigDecimal.ZERO
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
