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
    remainder: (LocalDate) -> List<PortfolioDailySummary>? = { null },
  ): List<PortfolioDailySummary> {
    val first = sortedDates.firstOrNull() ?: return emptyList()
    val from = minOf(dailySummaryCalculator.pricedDates(first).min(), sortedDates.last().minusYears(SHARED_PRICE_YEARS))
    val holdings = dailySummaryCalculator.holdingsByPrefix(sortedTransactions)
    val soldOut =
      holdings(sortedTransactions).filterNot { it.held }.associate { it.instrument.id to it.transactions.last().transactionDate }
    val priceLookup = buildPriceLookup(sortedTransactions, from, sortedDates.last(), soldOut)
    var transactionIndex = 0
    val summaries = ArrayList<PortfolioDailySummary>(sortedDates.size)
    for (date in sortedDates) {
      remainder(date)?.let { return summaries + it }
      while (transactionIndex < sortedTransactions.size &&
        !sortedTransactions[transactionIndex].transactionDate.isAfter(date)
      ) {
        transactionIndex++
      }
      val day = dailySummaryCalculator.prepareDay(sortedTransactions.subList(0, transactionIndex), date, priceLookup, holdings)
      summaries.add(dailySummaryCalculator.calculateFromPrepared(day, priceLookup))
    }
    return summaries
  }

  fun calculateSummaryOn(
    transactions: List<PortfolioTransaction>,
    date: LocalDate,
  ): PortfolioDailySummary {
    if (transactions.isEmpty()) return dailySummaryCalculator.createEmptySummary(date)
    val instruments = transactions.map { it.instrument }.distinct()
    val priceLookup = dailyPriceService.buildLatestPriceLookup(instruments, dailySummaryCalculator.pricedDates(date))
    return dailySummaryCalculator.calculateFromTransactions(transactions, date, priceLookup)
  }

  private fun buildPriceLookup(
    transactions: List<PortfolioTransaction>,
    from: LocalDate,
    until: LocalDate,
    soldOut: Map<Long, LocalDate>,
  ): PriceLookup =
    dailyPriceService.buildPriceLookup(
      transactions.groupBy { it.instrument.id }.mapValues { (id, history) -> history.minOf { it.transactionDate }..(soldOut[id] ?: until) },
      from,
    )

  fun processSummariesWithTransactions(
    dates: List<LocalDate>,
    allTransactions: List<PortfolioTransaction>,
    batchSize: Int = 30,
  ): Int {
    val first = dates.minOrNull() ?: return 0
    val priceLookup = buildPriceLookup(allTransactions, dailySummaryCalculator.pricedDates(first).min(), dates.max(), emptyMap())
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

  companion object {
    private const val SHARED_PRICE_YEARS = 5L
  }
}
