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
  private val portfolioTransactionRepository: PortfolioTransactionRepository,
  private val profitCalculationService: ProfitCalculationService
) {
  @Transactional(readOnly = true)
  @Cacheable(value = [TRANSACTION_CACHE], key = "#id")
  fun getTransactionById(id: Long): PortfolioTransaction =
    portfolioTransactionRepository.findById(id)
      .orElseThrow { RuntimeException("Transaction not found with id: $id") }

  @Transactional
  @Caching(
    evict = [
      CacheEvict(value = [TRANSACTION_CACHE], key = "#transaction.id", condition = "#transaction.id != null"),
      CacheEvict(value = [TRANSACTION_CACHE], key = "'transactions'")
    ]
  )
  fun saveTransaction(transaction: PortfolioTransaction): PortfolioTransaction {
    val saved = portfolioTransactionRepository.save(transaction)
    // Recalculate profits for all affected transactions
    val relatedTransactions = portfolioTransactionRepository
      .findAllByInstrumentIdAndPlatformOrderByTransactionDate(saved.instrument.id, saved.platform)
    profitCalculationService.calculateProfits(relatedTransactions)
    portfolioTransactionRepository.saveAll(relatedTransactions)
    return saved
  }

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
    val transactions = portfolioTransactionRepository.findAllWithInstruments()
    profitCalculationService.calculateProfits(transactions)
    return transactions
  }
}
