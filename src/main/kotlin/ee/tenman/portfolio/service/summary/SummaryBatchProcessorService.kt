package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.pricing.PriceLookup
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class SummaryBatchProcessorService(
  private val summaryPersistenceService: SummaryPersistenceService,
  private val entityManager: EntityManager,
  private val dailySummaryCalculator: DailySummaryCalculator,
  private val dailyPriceService: DailyPriceService,
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

  fun calculateSummaries(
    sortedDates: List<LocalDate>,
    sortedTransactions: List<PortfolioTransaction>,
  ): List<PortfolioDailySummary> {
    val priceLookup = dailyPriceService.buildPriceLookup(sortedTransactions.map { it.instrument }.distinct())
    var transactionIndex = 0
    val accumulated = mutableListOf<PortfolioTransaction>()
    return sortedDates.map { date ->
      while (transactionIndex < sortedTransactions.size &&
        !sortedTransactions[transactionIndex].transactionDate.isAfter(date)
      ) {
        accumulated.add(sortedTransactions[transactionIndex])
        transactionIndex++
      }
      dailySummaryCalculator.calculateFromTransactions(accumulated.toList(), date, priceLookup)
    }
  }

  fun processSummariesWithTransactions(
    dates: List<LocalDate>,
    allTransactions: List<PortfolioTransaction>,
    batchSize: Int = 30,
  ): Int {
    val priceLookup = dailyPriceService.buildPriceLookup(allTransactions.map { it.instrument }.distinct())
    return dates.chunked(batchSize).sumOf { batch ->
      processBatchWithTransactions(batch, allTransactions, priceLookup)
    }
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
    priceLookup: PriceLookup,
  ): Int {
    entityManager.clear()
    val sortedTransactions = allTransactions.sortedBy { it.transactionDate }
    val sortedDates = batch.sorted()
    var transactionIndex = 0
    val accumulatedTransactions = mutableListOf<PortfolioTransaction>()
    val summaries =
      sortedDates.mapNotNull { date ->
        while (transactionIndex < sortedTransactions.size &&
          !sortedTransactions[transactionIndex].transactionDate.isAfter(date)
        ) {
          accumulatedTransactions.add(sortedTransactions[transactionIndex])
          transactionIndex++
        }
        runCatching { dailySummaryCalculator.calculateFromTransactions(accumulatedTransactions.toList(), date, priceLookup) }
          .onFailure { log.warn("Failed to calculate summary for $date: ${it.message}") }
          .getOrNull()
      }
    if (summaries.isEmpty()) return 0
    return summaryPersistenceService.saveSummaries(summaries)
  }
}
