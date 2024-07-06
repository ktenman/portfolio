package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PortfolioTransactionService(private val portfolioTransactionRepository: PortfolioTransactionRepository) {

  @Transactional(readOnly = true)
  @Cacheable(value = [TRANSACTION_CACHE], key = "#id")
  fun getTransactionById(id: Long): PortfolioTransaction? = portfolioTransactionRepository.findById(id).orElse(null)

  @Transactional
  @Caching(
    evict = [
      CacheEvict(value = [TRANSACTION_CACHE], key = "#transaction.id", condition = "#transaction.id != null"),
      CacheEvict(value = [TRANSACTION_CACHE], key = "'transactions'")
    ]
  )
  fun saveTransaction(transaction: PortfolioTransaction): PortfolioTransaction = portfolioTransactionRepository.save(transaction)

  @Transactional
  @Caching(
    evict = [
      CacheEvict(value = [TRANSACTION_CACHE], key = "#id"),
      CacheEvict(value = [TRANSACTION_CACHE], key = "'transactions'")
    ]
  )
  fun deleteTransaction(id: Long) = portfolioTransactionRepository.deleteById(id)

  @Transactional(readOnly = true)
  @Cacheable(value = [TRANSACTION_CACHE], key = "'transactions'", unless = "#result.isEmpty()")
  fun getAllTransactions(): List<PortfolioTransaction> = portfolioTransactionRepository.findAll()
}
