package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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
  private val instrumentService: InstrumentService,
  private val cacheManager: CacheManager,
  private val investmentMetricsService: InvestmentMetricsService,
  private val clock: Clock,
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
  @Caching(
    evict = [
      CacheEvict(value = [SUMMARY_CACHE], key = "'summaries'"),
      CacheEvict(value = [SUMMARY_CACHE], allEntries = true),
    ],
  )
  fun deleteAllDailySummaries() {
    log.info("Deleting all portfolio daily summaries")
    portfolioDailySummaryRepository.deleteAll()
  }

  @Transactional
  @Caching(
    evict = [
      CacheEvict(value = [SUMMARY_CACHE], key = "'summaries'"),
      CacheEvict(value = [SUMMARY_CACHE], allEntries = true),
    ],
  )
  fun recalculateAllDailySummaries(): Int {
    log.info("Starting full recalculation of portfolio daily summaries")
    cacheManager.getCache(INSTRUMENT_CACHE)?.clear()

    val transactions =
      transactionService
      .getAllTransactions()
      .sortedBy { it.transactionDate }

    if (transactions.isEmpty()) {
      log.info("No transactions found. Nothing to recalculate.")
      return 0
    }

    val firstTransactionDate = transactions.first().transactionDate
    val today = LocalDate.now(clock)
    val yesterday = today.minusDays(1)
    log.info("Recalculating summaries from $firstTransactionDate to $yesterday (excluding current day)")

    deleteHistoricalSummaries(today)

    val datesToProcess = generateDateRange(firstTransactionDate, yesterday)
    val summariesSaved = processSummariesInBatches(datesToProcess)

    log.info("Successfully recalculated and saved $summariesSaved daily summaries (excluding current day)")
    return summariesSaved
  }

  @Transactional(readOnly = true)
  @Cacheable(value = [SUMMARY_CACHE], key = "'summaries'", unless = "#result.isEmpty()")
  fun getAllDailySummaries(): List<PortfolioDailySummary> = portfolioDailySummaryRepository.findAll()

  @Transactional(readOnly = true)
  @Cacheable(
    value = [SUMMARY_CACHE],
    key = "'summaries-page-' + #page + '-size-' + #size",
    unless = "#result.isEmpty()",
  )
  fun getAllDailySummaries(
    page: Int,
    size: Int,
  ): Page<PortfolioDailySummary> {
    val pageable = PageRequest.of(page, size, Sort.by("entryDate").descending())
    return portfolioDailySummaryRepository.findAll(pageable)
  }

  @Transactional(readOnly = true)
  fun getCurrentDaySummary(): PortfolioDailySummary {
    val today = LocalDate.now(clock)
    val instruments = instrumentService.getAllInstruments()

    val totalValue = instruments.sumOf { it.currentValue }
    val totalProfit = instruments.sumOf { it.profit }
    val xirrValue = instruments.maxByOrNull { it.currentValue }?.xirr ?: 0.0

    val xirrBigDecimal = BigDecimal(xirrValue).setScale(8, RoundingMode.HALF_UP)
    val earningsPerDay = calculateEarningsPerDay(totalValue, xirrBigDecimal)

    return createSummary(today, totalValue, xirrBigDecimal, totalProfit, earningsPerDay)
  }

  @Transactional(readOnly = true)
  fun calculateSummaryForDate(date: LocalDate): PortfolioDailySummary {
    if (isToday(date)) {
      return calculateTodaySummary(date)
    }

    val existingSummary = portfolioDailySummaryRepository.findByEntryDate(date)
    if (existingSummary != null) {
      return existingSummary
    }

    return calculateHistoricalSummary(date)
  }

  @Transactional
  @Caching(
    evict = [
      CacheEvict(value = [SUMMARY_CACHE], key = "'summaries'"),
      CacheEvict(value = [SUMMARY_CACHE], allEntries = true),
    ],
  )
  fun saveDailySummary(summary: PortfolioDailySummary): PortfolioDailySummary {
    val existing = portfolioDailySummaryRepository.findByEntryDate(summary.entryDate)
    val toSave =
      existing?.apply {
      totalValue = summary.totalValue
      xirrAnnualReturn = summary.xirrAnnualReturn
      totalProfit = summary.totalProfit
      earningsPerDay = summary.earningsPerDay
    } ?: summary

    return portfolioDailySummaryRepository.save(toSave)
  }

  @Transactional
  @Caching(
    evict = [
      CacheEvict(value = [SUMMARY_CACHE], key = "'summaries'"),
      CacheEvict(value = [SUMMARY_CACHE], allEntries = true),
    ],
  )
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
        totalProfit = s.totalProfit
        earningsPerDay = s.earningsPerDay
      } ?: s
    }

    portfolioDailySummaryRepository.saveAll(merged)
  }

  @Transactional(readOnly = true)
  fun getDailySummariesBetween(
    startDate: LocalDate,
    endDate: LocalDate,
  ) = portfolioDailySummaryRepository.findAllByEntryDateBetween(startDate, endDate)

  private fun deleteHistoricalSummaries(today: LocalDate) {
    portfolioDailySummaryRepository.findAll().forEach { summary ->
      if (summary.entryDate != today) {
        portfolioDailySummaryRepository.delete(summary)
      }
    }
    portfolioDailySummaryRepository.flush()
  }

  private fun generateDateRange(
    start: LocalDate,
    end: LocalDate,
  ): List<LocalDate> =
    generateSequence(start) { d ->
      d.plusDays(1).takeIf { !it.isAfter(end) }
    }.toList()

  private fun processSummariesInBatches(
    dates: List<LocalDate>,
    batchSize: Int = 30,
  ): Int =
    dates.chunked(batchSize).sumOf { batch ->
      val summaries = batch.map { calculateSummaryForDate(it) }
      portfolioDailySummaryRepository.saveAll(summaries)
      summaries.size
    }

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
    val previousSummary = portfolioDailySummaryRepository.findByEntryDate(previousDate)

    if (previousSummary != null) {
      val summary = calculateSummaryDetailsForDate(date)
      if (summary.totalValue.compareTo(previousSummary.totalValue) == 0) {
        return createSummary(
          date,
          summary.totalValue,
          summary.xirrAnnualReturn,
                           summary.totalProfit,
          summary.earningsPerDay,
        )
      }
      return summary
    }
    return null
  }

  private fun alignCurrentDaySummaryWithInstruments(summary: PortfolioDailySummary): PortfolioDailySummary {
    val instruments = instrumentService.getAllInstruments()
    val totalValue = instruments.sumOf { it.currentValue }
    val totalProfit = instruments.sumOf { it.profit }
    val earningsPerDay = calculateEarningsPerDay(totalValue, summary.xirrAnnualReturn)

    val updatedSummary =
      PortfolioDailySummary(
      entryDate = summary.entryDate,
      totalValue = totalValue,
      xirrAnnualReturn = summary.xirrAnnualReturn,
      totalProfit = totalProfit,
      earningsPerDay = earningsPerDay,
    )
    updatedSummary.id = summary.id
    updatedSummary.version = summary.version
    return updatedSummary
  }

  private fun createCurrentDaySummary(date: LocalDate): PortfolioDailySummary {
    val instruments = instrumentService.getAllInstruments()
    val totalValue = instruments.sumOf { it.currentValue }
    val totalProfit = instruments.sumOf { it.profit }

    val xirrTx = buildXirrTransactionsForDate(date)
    val xirr =
      if (xirrTx.size > 1) {
      investmentMetricsService.calculateAdjustedXirr(xirrTx, totalValue, date)
    } else {
      0.0
    }

    val xirrBigDecimal = BigDecimal(xirr).setScale(8, RoundingMode.HALF_UP)
    val earningsPerDay = calculateEarningsPerDay(totalValue, xirrBigDecimal)

    return createSummary(date, totalValue, xirrBigDecimal, totalProfit, earningsPerDay)
  }

  private fun buildXirrTransactionsForDate(date: LocalDate): List<ee.tenman.portfolio.service.xirr.Transaction> {
    val transactions =
      transactionService
      .getAllTransactions()
      .filter { !it.transactionDate.isAfter(date) }

    val instruments = instrumentService.getAllInstruments()
    val totalValue = instruments.sumOf { it.currentValue }

    return investmentMetricsService.buildXirrTransactions(transactions, totalValue, date)
  }

  private fun calculateSummaryDetailsForDate(date: LocalDate): PortfolioDailySummary {
    val transactions =
      transactionService
      .getAllTransactions()
      .filter { !it.transactionDate.isAfter(date) }
      .sortedBy { it.transactionDate }

    if (transactions.isEmpty()) {
      return createEmptySummary(date)
    }

    val instrumentGroups = transactions.groupBy { it.instrument }
    val results = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, date)

    val xirr =
      if (results.xirrTransactions.size > 1) {
      investmentMetricsService.calculateAdjustedXirr(results.xirrTransactions, results.totalValue, date)
    } else {
      0.0
    }
    val earningsPerDay = calculateEarningsPerDay(results.totalValue, BigDecimal(xirr))

    return PortfolioDailySummary(
      entryDate = date,
      totalValue = results.totalValue.setScale(10, RoundingMode.HALF_UP),
      xirrAnnualReturn = BigDecimal(xirr).setScale(8, RoundingMode.HALF_UP),
      totalProfit = results.totalProfit.setScale(10, RoundingMode.HALF_UP),
      earningsPerDay = earningsPerDay.setScale(10, RoundingMode.HALF_UP),
    )
  }

  private fun createSummary(
    date: LocalDate,
    totalValue: BigDecimal,
    xirrAnnualReturn: BigDecimal,
    totalProfit: BigDecimal,
    earningsPerDay: BigDecimal,
  ): PortfolioDailySummary {
    val existingSummary = portfolioDailySummaryRepository.findByEntryDate(date)
    val summary =
      PortfolioDailySummary(
      entryDate = date,
      totalValue = totalValue,
      xirrAnnualReturn = xirrAnnualReturn,
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
      totalProfit = BigDecimal.ZERO,
      earningsPerDay = BigDecimal.ZERO,
    )
}
