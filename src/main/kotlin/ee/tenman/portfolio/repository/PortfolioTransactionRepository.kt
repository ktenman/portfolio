package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.PortfolioTransaction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface PortfolioTransactionRepository : JpaRepository<PortfolioTransaction, Long> {
  fun findByInstrumentIdAndTransactionDateBetween(instrumentId: Long, startDate: LocalDate, endDate: LocalDate): List<PortfolioTransaction>
}
