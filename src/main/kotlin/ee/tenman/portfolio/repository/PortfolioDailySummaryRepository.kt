package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.PortfolioDailySummary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface PortfolioDailySummaryRepository : JpaRepository<PortfolioDailySummary, Long> {
  // NOTE: save() is already provided by JpaRepository - removed redundant declaration

  fun findAllByEntryDateIn(dates: List<LocalDate>): List<PortfolioDailySummary>

  fun findAllByEntryDateBetween(
    startDate: LocalDate,
    endDate: LocalDate,
  ): List<PortfolioDailySummary>

  fun findByEntryDate(entryDate: LocalDate): PortfolioDailySummary?
}
