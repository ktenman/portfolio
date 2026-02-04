package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import ee.tenman.portfolio.service.transaction.TransactionService
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

@Service
class SummaryService(
  private val portfolioDailySummaryRepository: PortfolioDailySummaryRepository,
  private val transactionService: TransactionService,
  private val cacheManager: CacheManager,
  private val clock: Clock,
  private val summaryBatchProcessor: SummaryBatchProcessorService,
  private val summaryDeletionService: SummaryDeletionService,
  private val summaryCacheService: SummaryCacheService,
  private val dailySummaryCalculator: DailySummaryCalculator,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  fun deleteAllDailySummaries() {
    log.info("Deleting all portfolio daily summaries")
    portfolioDailySummaryRepository.deleteAll()
    summaryCacheService.evictAllCaches()
  }

  fun recalculateAllDailySummaries(): Int {
    log.info("Starting full recalculation of portfolio daily summaries")
    clearAllCaches()
    val transactions =
      transactionService
        .getAllTransactions()
        .sortedWith(compareBy({ it.transactionDate }, { it.id }))
    if (transactions.isEmpty()) {
      log.info("No transactions found. Nothing to recalculate")
      return 0
    }
    val firstTransactionDate = transactions.first().transactionDate
    val today = LocalDate.now(clock)
    val yesterday = today.minusDays(1)
    log.info("Recalculating summaries from $firstTransactionDate to $yesterday (excluding current day)")
    summaryDeletionService.deleteHistoricalSummaries(today)
    val datesToProcess = generateDateRange(firstTransactionDate, yesterday)
    val summariesSaved = summaryBatchProcessor.processSummariesWithTransactions(datesToProcess, transactions)
    log.info("Successfully recalculated and saved $summariesSaved daily summaries (excluding current day)")
    clearAllCaches()
    return summariesSaved
  }

  private fun clearAllCaches() {
    cacheManager.getCache(INSTRUMENT_CACHE)?.clear()
    cacheManager.getCache(TRANSACTION_CACHE)?.clear()
    summaryCacheService.evictAllCaches()
  }

  @Transactional(readOnly = true)
  fun getCurrentDaySummary(): PortfolioDailySummary = calculateSummaryDetailsForDate(LocalDate.now(clock))

  fun calculateSummaryForDate(date: LocalDate): PortfolioDailySummary {
    if (isToday(date)) return calculateTodaySummary(date)
    val existingSummary = summaryCacheService.findByEntryDate(date)
    if (existingSummary != null) return existingSummary
    return calculateHistoricalSummary(date)
  }

  @Transactional
  fun saveDailySummary(summary: PortfolioDailySummary): PortfolioDailySummary {
    val existing = portfolioDailySummaryRepository.findByEntryDate(summary.entryDate)
    val toSave = existing?.mergeFrom(summary) ?: summary
    val saved = portfolioDailySummaryRepository.save(toSave)
    summaryCacheService.evictAllCaches()
    return saved
  }

  @Transactional
  fun saveDailySummaries(summaries: List<PortfolioDailySummary>) {
    val existing =
      portfolioDailySummaryRepository
        .findAllByEntryDateIn(summaries.map { it.entryDate })
        .associateBy { it.entryDate }
    val merged = summaries.map { s -> existing[s.entryDate]?.mergeFrom(s) ?: s }
    portfolioDailySummaryRepository.saveAll(merged)
    summaryCacheService.evictAllCaches()
  }

  @Transactional(readOnly = true)
  fun getDailySummariesBetween(
    startDate: LocalDate,
    endDate: LocalDate,
  ) = portfolioDailySummaryRepository.findAllByEntryDateBetween(startDate, endDate)

  private fun generateDateRange(
    start: LocalDate,
    end: LocalDate,
  ): List<LocalDate> =
    generateSequence(start) { d ->
      d.plusDays(1).takeIf { !it.isAfter(end) }
    }.toList()

  private fun isToday(date: LocalDate): Boolean = date.isEqual(LocalDate.now(clock))

  private fun calculateTodaySummary(date: LocalDate): PortfolioDailySummary {
    val existingSummary = summaryCacheService.findByEntryDate(date)
    return existingSummary?.let { alignCurrentDaySummaryWithInstruments(it) }
      ?: calculateSummaryDetailsForDate(date)
  }

  private fun calculateHistoricalSummary(date: LocalDate): PortfolioDailySummary {
    val transactionsOnDate =
      transactionService
      .getAllTransactions()
      .filter { it.transactionDate.isEqual(date) }

    if (transactionsOnDate.isEmpty()) {
      val previousSummary = tryUsePreviousDaySummary(date)
      if (previousSummary != null) {
        return previousSummary
      }
    }

    return calculateSummaryDetailsForDate(date)
  }

  private fun tryUsePreviousDaySummary(date: LocalDate): PortfolioDailySummary? {
    val previousDate = date.minusDays(1)
    val previousSummary = summaryCacheService.findByEntryDate(previousDate) ?: return null
    val summary = calculateSummaryDetailsForDate(date)
    if (dailySummaryCalculator.shouldReuseYesterday(previousSummary, summary)) {
      return createSummaryFromExisting(date, summary)
    }
    return summary
  }

  private fun alignCurrentDaySummaryWithInstruments(summary: PortfolioDailySummary): PortfolioDailySummary {
    val newSummary = calculateSummaryDetailsForDate(summary.entryDate)
    newSummary.id = summary.id
    newSummary.version = summary.version
    return newSummary
  }

  private fun calculateSummaryDetailsForDate(date: LocalDate): PortfolioDailySummary {
    val transactions =
      transactionService
        .getAllTransactions()
        .filter { !it.transactionDate.isAfter(date) }
        .sortedWith(compareBy({ it.transactionDate }, { it.id }))
    return dailySummaryCalculator.calculateFromTransactions(transactions, date)
  }

  private fun createSummaryFromExisting(
    date: LocalDate,
    source: PortfolioDailySummary,
  ): PortfolioDailySummary {
    val existingSummary = summaryCacheService.findByEntryDate(date)
    val summary =
      PortfolioDailySummary(
        entryDate = date,
        totalValue = source.totalValue,
        xirrAnnualReturn = source.xirrAnnualReturn,
        realizedProfit = source.realizedProfit,
        unrealizedProfit = source.unrealizedProfit,
        totalProfit = source.totalProfit,
        earningsPerDay = source.earningsPerDay,
      )
    if (existingSummary != null) {
      summary.id = existingSummary.id
      summary.version = existingSummary.version
    }
    return summary
  }

  private fun PortfolioDailySummary.mergeFrom(source: PortfolioDailySummary) =
    apply {
      totalValue = source.totalValue
      xirrAnnualReturn = source.xirrAnnualReturn
      realizedProfit = source.realizedProfit
      unrealizedProfit = source.unrealizedProfit
      totalProfit = source.totalProfit
      earningsPerDay = source.earningsPerDay
    }
}
