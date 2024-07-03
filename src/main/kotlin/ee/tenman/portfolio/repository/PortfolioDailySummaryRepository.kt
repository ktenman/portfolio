package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.PortfolioDailySummary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface PortfolioDailySummaryRepository : JpaRepository<PortfolioDailySummary, Long> {
  fun findByEntryDateBetween(startDate: LocalDate, endDate: LocalDate): List<PortfolioDailySummary>
}