package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class SummaryBatchProcessorService(
  private val summaryPersistenceService: SummaryPersistenceService,
  private val entityManager: EntityManager,
  private val dailySummaryCalculator: DailySummaryCalculator,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun processSummariesInBatches(
    dates: List<LocalDate>,
    summaryCalculator: (LocalDate) -> PortfolioDailySummary,
    batchSize: Int = 30,
  ): Int =
    dates.chunked(batchSize).sumOf { batch ->
      processBatch(batch, summaryCalculator)
    }

  fun processSummariesWithTransactions(
    dates: List<LocalDate>,
    allTransactions: List<PortfolioTransaction>,
    batchSize: Int = 30,
  ): Int =
    dates.chunked(batchSize).sumOf { batch ->
      processBatchWithTransactions(batch, allTransactions)
    }

  private fun processBatch(
    batch: List<LocalDate>,
    summaryCalculator: (LocalDate) -> PortfolioDailySummary,
  ): Int {
    entityManager.clear()
    val summaries =
      batch.mapNotNull { date ->
        runCatching { summaryCalculator(date) }
          .onFailure { log.warn("Failed to calculate summary for $date: ${it.message}") }
          .getOrNull()
      }
    if (summaries.isEmpty()) return 0
    return summaryPersistenceService.saveSummaries(summaries)
  }

  private fun processBatchWithTransactions(
    batch: List<LocalDate>,
    allTransactions: List<PortfolioTransaction>,
  ): Int {
    entityManager.clear()
    val summaries =
      batch.mapNotNull { date ->
        runCatching { calculateSummaryForDate(date, allTransactions) }
          .onFailure { log.warn("Failed to calculate summary for $date: ${it.message}") }
          .getOrNull()
      }
    if (summaries.isEmpty()) return 0
    return summaryPersistenceService.saveSummaries(summaries)
  }

  private fun calculateSummaryForDate(
    date: LocalDate,
    allTransactions: List<PortfolioTransaction>,
  ): PortfolioDailySummary {
    val transactionsUpToDate = allTransactions.filter { !it.transactionDate.isAfter(date) }
    return dailySummaryCalculator.calculateFromTransactions(transactionsUpToDate, date)
  }
}
