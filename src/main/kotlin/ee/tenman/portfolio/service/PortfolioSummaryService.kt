package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class PortfolioSummaryService(private val portfolioDailySummaryRepository: PortfolioDailySummaryRepository) {

  @Transactional(readOnly = true)
  fun getAllDailySummaries(): List<PortfolioDailySummary> = portfolioDailySummaryRepository.findAll()

  @Transactional
  fun saveDailySummary(dailySummary: PortfolioDailySummary): PortfolioDailySummary {
    val existingSummary = portfolioDailySummaryRepository.findByEntryDate(dailySummary.entryDate)

    return existingSummary?.apply {
      totalValue = dailySummary.totalValue
      xirrAnnualReturn = dailySummary.xirrAnnualReturn
      totalProfit = dailySummary.totalProfit
      earningsPerDay = dailySummary.earningsPerDay
    }?.let { portfolioDailySummaryRepository.save(it) }
      ?: portfolioDailySummaryRepository.save(dailySummary)
  }

  @Transactional(readOnly = true)
  fun getLastCalculatedDate(): LocalDate? {
    return portfolioDailySummaryRepository.findTopByOrderByEntryDateDesc()?.entryDate
  }

  @Transactional
  fun saveDailySummaries(summaries: List<PortfolioDailySummary>) {
    val existingSummaries = portfolioDailySummaryRepository.findAllByEntryDateIn(summaries.map { it.entryDate })
      .associateBy { it.entryDate }

    val updatedSummaries = summaries.map { newSummary ->
      existingSummaries[newSummary.entryDate]?.apply {
        totalValue = newSummary.totalValue
        xirrAnnualReturn = newSummary.xirrAnnualReturn
        totalProfit = newSummary.totalProfit
        earningsPerDay = newSummary.earningsPerDay
      } ?: newSummary
    }

    portfolioDailySummaryRepository.saveAll(updatedSummaries)
  }

  @Transactional(readOnly = true)
  fun getDailySummary(date: LocalDate): PortfolioDailySummary? {
    return portfolioDailySummaryRepository.findByEntryDate(date)
  }
}
