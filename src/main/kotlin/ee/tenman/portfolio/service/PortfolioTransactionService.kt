package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class PortfolioTransactionService(
  private val portfolioTransactionRepository: PortfolioTransactionRepository,
  private val profitCalculationService: ProfitCalculationService
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional(readOnly = true)
  @Cacheable(value = [TRANSACTION_CACHE], key = "#id")
  fun getTransactionById(id: Long): PortfolioTransaction =
    portfolioTransactionRepository.findById(id)
      .orElseThrow { RuntimeException("Transaction not found with id: $id") }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  @Retryable(
    value = [ObjectOptimisticLockingFailureException::class],
    maxAttempts = 5,
    backoff = Backoff(delay = 100)
  )
  @Caching(
    evict = [
      CacheEvict(value = [TRANSACTION_CACHE], key = "#transaction.id", condition = "#transaction.id != null"),
      CacheEvict(value = [TRANSACTION_CACHE], key = "'transactions'")
    ]
  )
  fun saveTransaction(transaction: PortfolioTransaction): PortfolioTransaction {
    try {
      val saved = portfolioTransactionRepository.save(transaction)
      val relatedTransactions = portfolioTransactionRepository
        .findAllByInstrumentIdAndPlatformOrderByTransactionDate(saved.instrument.id, saved.platform)
      profitCalculationService.calculateProfits(relatedTransactions)
      return portfolioTransactionRepository.saveAll(relatedTransactions)
        .find { it.id == saved.id }!!
    } catch (e: ObjectOptimisticLockingFailureException) {
      log.warn("Optimistic locking failure while saving transaction. Will retry.", e)
      throw e
    }
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  @Retryable(
    value = [ObjectOptimisticLockingFailureException::class],
    maxAttempts = 5,
    backoff = Backoff(delay = 100)
  )
  @Caching(
    evict = [
      CacheEvict(value = [TRANSACTION_CACHE], key = "#id"),
      CacheEvict(value = [TRANSACTION_CACHE], key = "'transactions'")
    ]
  )
  fun deleteTransaction(id: Long) {
    try {
      portfolioTransactionRepository.deleteById(id)
    } catch (e: ObjectOptimisticLockingFailureException) {
      log.warn("Optimistic locking failure while deleting transaction. Will retry.", e)
      throw e
    }
  }

  @Transactional(readOnly = true)
  fun getAllTransactions(): List<PortfolioTransaction> {
    val transactions = portfolioTransactionRepository.findAllWithInstruments()
    profitCalculationService.calculateProfits(transactions)
    return transactions
  }
}
