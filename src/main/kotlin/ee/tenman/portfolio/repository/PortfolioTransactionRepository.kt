package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.PortfolioTransaction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PortfolioTransactionRepository : JpaRepository<PortfolioTransaction, Long> {
  @Query("SELECT pt FROM PortfolioTransaction pt JOIN FETCH pt.instrument")
  fun findAllWithInstruments(): List<PortfolioTransaction>
}
