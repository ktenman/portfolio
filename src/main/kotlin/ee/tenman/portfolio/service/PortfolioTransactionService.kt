package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class PortfolioTransactionService(
  private val portfolioTransactionRepository: PortfolioTransactionRepository
) {

  @Transactional(readOnly = true)
  @Cacheable(value = [TRANSACTION_CACHE], key = "#id")
  fun getTransactionById(id: Long): PortfolioTransaction? = portfolioTransactionRepository.findById(id)
    .map { transaction ->
      transaction.apply {
        currentValue = calculateCurrentValue(this)
        profit = calculateProfit(this)
      }
    }
    .orElseThrow( { RuntimeException("Transaction not found with id: $id") })

  @Transactional
  @Caching(
    evict = [
      CacheEvict(value = [TRANSACTION_CACHE], key = "#transaction.id", condition = "#transaction.id != null"),
      CacheEvict(value = [TRANSACTION_CACHE], key = "'transactions'")
    ]
  )
  fun saveTransaction(transaction: PortfolioTransaction): PortfolioTransaction =
    portfolioTransactionRepository.save(transaction)

  @Transactional
  @Caching(
    evict = [
      CacheEvict(value = [TRANSACTION_CACHE], key = "#id"),
      CacheEvict(value = [TRANSACTION_CACHE], key = "'transactions'")
    ]
  )
  fun deleteTransaction(id: Long) = portfolioTransactionRepository.deleteById(id)

  @Transactional(readOnly = true)
  fun getAllTransactions(): List<PortfolioTransaction> {
    return portfolioTransactionRepository.findAllWithInstruments().map { transaction ->
      transaction.apply {
        currentValue = calculateCurrentValue(this)
        profit = calculateProfit(this)
      }
    }
  }

  fun calculateCurrentValue(transaction: PortfolioTransaction): BigDecimal {
    val currentPrice = transaction.instrument.currentPrice ?: BigDecimal.ZERO
    return when (transaction.transactionType) {
      TransactionType.BUY -> transaction.quantity.multiply(currentPrice)
      TransactionType.SELL -> BigDecimal.ZERO
    }
  }

  fun calculateProfit(transaction: PortfolioTransaction): BigDecimal {
    if (transaction.transactionType == TransactionType.SELL) {
      return BigDecimal.ZERO
    }

    val currentPrice = transaction.instrument.currentPrice ?: return BigDecimal.ZERO
    val initialInvestment = transaction.quantity.multiply(transaction.price)
    val currentValue = transaction.quantity.multiply(currentPrice)

    return currentValue.subtract(initialInvestment).setScale(2, RoundingMode.HALF_UP)
  }

}
