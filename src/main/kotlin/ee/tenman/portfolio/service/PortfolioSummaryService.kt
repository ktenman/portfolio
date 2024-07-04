package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PortfolioSummaryService(private val portfolioDailySummaryRepository: PortfolioDailySummaryRepository) {

  @Transactional(readOnly = true)
  fun getAllDailySummaries(): List<PortfolioDailySummary> = portfolioDailySummaryRepository.findAll()

  @Transactional
  fun saveDailySummary(dailySummary: PortfolioDailySummary): PortfolioDailySummary = portfolioDailySummaryRepository.save(dailySummary)

}
