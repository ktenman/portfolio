package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class SummaryDeletionService(
  private val portfolioDailySummaryRepository: PortfolioDailySummaryRepository,
) {
  @Transactional
  fun deleteHistoricalSummaries(today: LocalDate) {
    portfolioDailySummaryRepository.deleteByEntryDateNot(today)
    portfolioDailySummaryRepository.flush()
  }
}
