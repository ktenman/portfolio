package ee.tenman.portfolio.service.transaction

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class TransactionCacheService(
  private val portfolioTransactionRepository: PortfolioTransactionRepository,
) {
  @Cacheable(value = [TRANSACTION_CACHE], key = "'transactions'", unless = "#result.isEmpty()")
  fun getAllTransactions(): List<PortfolioTransaction> = portfolioTransactionRepository.findAllNewestFirst()

  @Cacheable(value = [TRANSACTION_CACHE], key = "'platforms'", unless = "#result.isEmpty()")
  fun getDistinctPlatforms(): List<Platform> = portfolioTransactionRepository.findDistinctPlatforms()
}
