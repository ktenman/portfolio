package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class PortfolioDailySummaryService(private val portfolioDailySummaryRepository: PortfolioDailySummaryRepository) {

  @Transactional(readOnly = true)
  fun getDailySummaryById(id: Long): PortfolioDailySummary? = portfolioDailySummaryRepository.findById(id).orElse(null)

  @Transactional(readOnly = true)
  fun getDailySummariesByDateRange(startDate: LocalDate, endDate: LocalDate): List<PortfolioDailySummary> =
    portfolioDailySummaryRepository.findByEntryDateBetween(startDate, endDate)

  @Transactional
  fun saveDailySummary(dailySummary: PortfolioDailySummary): PortfolioDailySummary = portfolioDailySummaryRepository.save(dailySummary)

  @Transactional
  fun deleteDailySummary(id: Long) = portfolioDailySummaryRepository.deleteById(id)
}
