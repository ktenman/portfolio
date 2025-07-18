package ee.tenman.portfolio.repository

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PortfolioTransactionRepository : JpaRepository<PortfolioTransaction, Long> {
  @Query("SELECT pt FROM PortfolioTransaction pt JOIN FETCH pt.instrument")
  @Cacheable(value = [TRANSACTION_CACHE], key = "'transactions'", unless = "#result.isEmpty()")
  fun findAllWithInstruments(): List<PortfolioTransaction>

  fun findAllByInstrumentIdAndPlatformOrderByTransactionDate(
    instrumentId: Long,
    platform: Platform,
  ): List<PortfolioTransaction>
}
