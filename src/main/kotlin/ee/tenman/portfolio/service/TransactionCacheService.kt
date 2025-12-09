package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import jakarta.persistence.EntityManager
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TransactionCacheService(
  private val portfolioTransactionRepository: PortfolioTransactionRepository,
  private val entityManager: EntityManager,
) {
  @Transactional(readOnly = true)
  @Cacheable(value = [TRANSACTION_CACHE], key = "'transactions'", unless = "#result.isEmpty()")
  fun getAllTransactions(): List<PortfolioTransaction> {
    val transactions = portfolioTransactionRepository.findAllWithInstruments()
    transactions.forEach { tx ->
      entityManager.detach(tx.instrument)
      entityManager.detach(tx)
    }
    return transactions
  }
}
