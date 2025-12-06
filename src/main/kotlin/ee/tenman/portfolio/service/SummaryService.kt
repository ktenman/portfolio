package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate

@Service
class SummaryService(
  private val portfolioDailySummaryRepository: PortfolioDailySummaryRepository,
  private val transactionService: TransactionService,
  private val cacheManager: CacheManager,
  private val investmentMetricsService: InvestmentMetricsService,
  private val xirrCalculationService: XirrCalculationService,
  private val clock: Clock,
  private val summaryBatchProcessor: SummaryBatchProcessorService,
  private val summaryDeletionService: SummaryDeletionService,
  private val summaryCacheService: SummaryCacheService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  private fun calculateEarningsPerDay(
    totalValue: BigDecimal,
    xirrRate: BigDecimal,
  ): BigDecimal =
    totalValue
      .multiply(xirrRate)
      .divide(BigDecimal(365.25), 10, RoundingMode.HALF_UP)

  @Transactional
  fun deleteAllDailySummaries() {
    log.info("Deleting all portfolio daily summaries")
    portfolioDailySummaryRepository.deleteAll()
    summaryCacheService.evictAllCaches()
  }

  fun recalculateAllDailySummaries(): Int {
    log.info("Starting full recalculation of portfolio daily summaries")
    cacheManager.getCache(INSTRUMENT_CACHE)?.clear()
    summaryCacheService.evictAllCaches()

    val transactions =
      transactionService
      .getAllTransactions()
      .sortedWith(compareBy({ it.transactionDate }, { it.id }))

    if (transactions.isEmpty()) {
      log.info("No transactions found. Nothing to recalculate.")
      return 0
    }

    val firstTransactionDate = transactions.first().transactionDate
    val today = LocalDate.now(clock)
    val yesterday = today.minusDays(1)
    log.info("Recalculating summaries from $firstTransactionDate to $yesterday (excluding current day)")

    summaryDeletionService.deleteHistoricalSummaries(today)

    val datesToProcess = generateDateRange(firstTransactionDate, yesterday)
    val summariesSaved = summaryBatchProcessor.processSummariesInBatches(datesToProcess, ::calculateSummaryForDate)

    log.info("Successfully recalculated and saved $summariesSaved daily summaries (excluding current day)")
    return summariesSaved
  }

  @Transactional(readOnly = true)
  fun getCurrentDaySummary(): PortfolioDailySummary {
    val today = LocalDate.now(clock)
    return calculateCurrentDaySummaryForDate(today)
  }

  fun calculateSummaryForDate(date: LocalDate): PortfolioDailySummary {
    if (isToday(date)) return calculateTodaySummary(date)

    val existingSummary = portfolioDailySummaryRepository.findByEntryDate(date)
    if (existingSummary != null) return existingSummary

    return calculateHistoricalSummary(date)
  }

  @Transactional
  fun saveDailySummary(summary: PortfolioDailySummary): PortfolioDailySummary {
    val existing = portfolioDailySummaryRepository.findByEntryDate(summary.entryDate)
    val toSave =
      existing?.apply {
      totalValue = summary.totalValue
      xirrAnnualReturn = summary.xirrAnnualReturn
      realizedProfit = summary.realizedProfit
      unrealizedProfit = summary.unrealizedProfit
      totalProfit = summary.totalProfit
      earningsPerDay = summary.earningsPerDay
    } ?: summary

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

    val merged =
      summaries.map { s ->
      existing[s.entryDate]?.apply {
        totalValue = s.totalValue
        xirrAnnualReturn = s.xirrAnnualReturn
        realizedProfit = s.realizedProfit
        unrealizedProfit = s.unrealizedProfit
        totalProfit = s.totalProfit
        earningsPerDay = s.earningsPerDay
      } ?: s
    }

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
    val existingSummary = portfolioDailySummaryRepository.findByEntryDate(date)
    return existingSummary?.let {
      alignCurrentDaySummaryWithInstruments(it)
    } ?: createCurrentDaySummary(date)
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
    val previousSummary = portfolioDailySummaryRepository.findByEntryDate(previousDate) ?: return null

    val summary = calculateSummaryDetailsForDate(date)
    if (summary.totalValue.compareTo(previousSummary.totalValue) == 0) {
      return createSummary(
        date,
        summary.totalValue,
        summary.xirrAnnualReturn,
        summary.realizedProfit,
        summary.unrealizedProfit,
        summary.totalProfit,
        summary.earningsPerDay,
      )
    }
    return summary
  }

  private fun calculateCurrentDaySummaryForDate(date: LocalDate): PortfolioDailySummary = calculateSummaryDetailsForDate(date)

  private fun alignCurrentDaySummaryWithInstruments(summary: PortfolioDailySummary): PortfolioDailySummary {
    val newSummary = calculateSummaryDetailsForDate(summary.entryDate)
    newSummary.id = summary.id
    newSummary.version = summary.version
    return newSummary
  }

  private fun createCurrentDaySummary(date: LocalDate): PortfolioDailySummary = calculateSummaryDetailsForDate(date)

  private fun calculateSummaryDetailsForDate(date: LocalDate): PortfolioDailySummary {
    val transactions =
      transactionService
      .getAllTransactions()
      .filter { !it.transactionDate.isAfter(date) }
      .sortedWith(compareBy({ it.transactionDate }, { it.id }))

    if (transactions.isEmpty()) {
      return createEmptySummary(date)
    }

    val instrumentGroups = transactions.groupBy { it.instrument }
    val results = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, date)

    val xirr = xirrCalculationService.calculateAdjustedXirr(results.xirrTransactions, date)
    val earningsPerDay = calculateEarningsPerDay(results.totalValue, BigDecimal(xirr))

    return PortfolioDailySummary(
      entryDate = date,
      totalValue = results.totalValue.setScale(10, RoundingMode.HALF_UP),
      xirrAnnualReturn = BigDecimal(xirr).setScale(10, RoundingMode.HALF_UP),
      realizedProfit = results.realizedProfit.setScale(10, RoundingMode.HALF_UP),
      unrealizedProfit = results.unrealizedProfit.setScale(10, RoundingMode.HALF_UP),
      totalProfit = results.totalProfit.setScale(10, RoundingMode.HALF_UP),
      earningsPerDay = earningsPerDay.setScale(10, RoundingMode.HALF_UP),
    )
  }

  private fun createSummary(
    date: LocalDate,
    totalValue: BigDecimal,
    xirrAnnualReturn: BigDecimal,
    realizedProfit: BigDecimal,
    unrealizedProfit: BigDecimal,
    totalProfit: BigDecimal,
    earningsPerDay: BigDecimal,
  ): PortfolioDailySummary {
    val existingSummary = portfolioDailySummaryRepository.findByEntryDate(date)
    val summary =
      PortfolioDailySummary(
      entryDate = date,
      totalValue = totalValue,
      xirrAnnualReturn = xirrAnnualReturn,
      realizedProfit = realizedProfit,
      unrealizedProfit = unrealizedProfit,
      totalProfit = totalProfit,
      earningsPerDay = earningsPerDay,
    )

    if (existingSummary != null) {
      summary.id = existingSummary.id
      summary.version = existingSummary.version
    }

    return summary
  }

  private fun createEmptySummary(date: LocalDate): PortfolioDailySummary =
    PortfolioDailySummary(
      entryDate = date,
      totalValue = BigDecimal.ZERO,
      xirrAnnualReturn = BigDecimal.ZERO,
      realizedProfit = BigDecimal.ZERO,
      unrealizedProfit = BigDecimal.ZERO,
      totalProfit = BigDecimal.ZERO,
      earningsPerDay = BigDecimal.ZERO,
    )
}
