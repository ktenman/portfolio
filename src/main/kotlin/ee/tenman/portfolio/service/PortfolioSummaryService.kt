package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import ee.tenman.portfolio.service.xirr.Transaction
import org.slf4j.LoggerFactory
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
class PortfolioSummaryService(
  private val portfolioDailySummaryRepository: PortfolioDailySummaryRepository,
  private val portfolioTransactionService: PortfolioTransactionService,
  private val dailyPriceService: DailyPriceService,
  private val unifiedProfitCalculationService: UnifiedProfitCalculationService,
  private val investmentMetricsService: InvestmentMetricsService,
  private val clock: Clock
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  @Caching(
    evict = [
      CacheEvict(value = [SUMMARY_CACHE], key = "'summaries'"),
      CacheEvict(value = [SUMMARY_CACHE], allEntries = true)
    ]
  )
  fun deleteAllDailySummaries() {
    log.info("Deleting all portfolio daily summaries")
    portfolioDailySummaryRepository.deleteAll()
  }

  @Transactional
  fun recalculateAllDailySummaries(): Int {
    log.info("Starting full recalculation of portfolio daily summaries")

    // Get all transactions to determine date range
    val transactions = portfolioTransactionService.getAllTransactions()
      .sortedBy { it.transactionDate }

    if (transactions.isEmpty()) {
      log.info("No transactions found. Nothing to recalculate.")
      return 0
    }

    // Determine date range
    val firstTransactionDate = transactions.first().transactionDate
    val today = LocalDate.now(clock)

    log.info("Recalculating summaries from $firstTransactionDate to $today")

    try {
      // First safely delete all existing summaries
      log.info("Deleting existing daily summaries")
      portfolioDailySummaryRepository.deleteAll()
      portfolioDailySummaryRepository.flush() // Ensure delete is committed to database

      // Generate all dates between first transaction and today
      val datesToProcess = generateSequence(firstTransactionDate) { date ->
        val next = date.plusDays(1)
        if (next.isAfter(today)) null else next
      }.toList()

      log.info("Processing ${datesToProcess.size} days of data")

      // Process dates in smaller batches to avoid overwhelming the database
      val batchSize = 30
      val summariesSaved = datesToProcess.chunked(batchSize).sumOf { dateBatch ->
        val summariesToSave = dateBatch.map { currentDate ->
          log.debug("Calculating summary for date: {}", currentDate)
          calculateSummaryForDate(currentDate)
        }

        // Save batch and return count
        if (summariesToSave.isNotEmpty()) {
          portfolioDailySummaryRepository.saveAll(summariesToSave)
          log.debug("Saved batch of ${summariesToSave.size} summaries")
        }

        summariesToSave.size
      }

      log.info("Successfully recalculated and saved $summariesSaved daily summaries")
      return summariesSaved

    } catch (e: Exception) {
      log.error("Error during summary recalculation", e)
      throw e
    }
  }

  @Transactional(readOnly = true)
  @Cacheable(value = [SUMMARY_CACHE], key = "'summaries'", unless = "#result.isEmpty()")
  fun getAllDailySummaries(): List<PortfolioDailySummary> = portfolioDailySummaryRepository.findAll()

  @Transactional(readOnly = true)
  @Cacheable(
    value = [SUMMARY_CACHE],
    key = "'summaries-page-' + #page + '-size-' + #size",
    unless = "#result.isEmpty()"
  )
  fun getAllDailySummaries(page: Int, size: Int): Page<PortfolioDailySummary> {
    val pageable = PageRequest.of(page, size, Sort.by("entryDate").descending())
    return portfolioDailySummaryRepository.findAll(pageable)
  }

  @Transactional(readOnly = true)
  fun getCurrentDaySummary(): PortfolioDailySummary {
    val currentDate = LocalDate.now(clock)
    return calculateSummaryForDate(currentDate)
  }

  @Transactional(readOnly = true)
  fun calculateSummaryForDate(date: LocalDate): PortfolioDailySummary {
    val transactions = portfolioTransactionService.getAllTransactions()
      .filter { !it.transactionDate.isAfter(date) }
      .sortedBy { it.transactionDate }

    // Get all instruments that have transactions up to this date
    val instruments = transactions.map { it.instrument }.distinct()

    // Group transactions by instrument for accurate profit calculation
    val transactionsByInstrument = transactions.groupBy { it.instrument }

    // Use InvestmentMetricsService for calculating consistent profits
    var totalValue = BigDecimal.ZERO
    var totalProfit = BigDecimal.ZERO
    val xirrTransactions = mutableListOf<Transaction>()

    instruments.forEach { instrument ->
      val instrumentTransactions = transactionsByInstrument[instrument] ?: emptyList()

      // Use the same metrics calculation as used on the instruments page
      val metrics = investmentMetricsService.calculateInstrumentMetrics(instrument, instrumentTransactions)

      totalValue = totalValue.add(metrics.currentValue)
      totalProfit = totalProfit.add(metrics.profit)

      // Add transactions for XIRR calculation
      instrumentTransactions.forEach { transaction ->
        val amount = transaction.price.multiply(transaction.quantity)
        xirrTransactions.add(
          Transaction(
            when (transaction.transactionType) {
              TransactionType.BUY -> -amount
              TransactionType.SELL -> amount
            }.toDouble(),
            transaction.transactionDate
          )
        )
      }

      // Add current value for XIRR calculation if there are holdings
      if (metrics.quantity > BigDecimal.ZERO) {
        xirrTransactions.add(Transaction(metrics.currentValue.toDouble(), date))
      }
    }

    // Calculate XIRR with time-weighted adjustment for better accuracy
    val xirrResult = if (xirrTransactions.size > 1) {
      unifiedProfitCalculationService.calculateAdjustedXirr(xirrTransactions, totalValue)
    } else 0.0

    log.debug("Date: $date, Total Value: $totalValue, Total Profit: $totalProfit")
    log.debug("XIRR: $xirrResult")

    return PortfolioDailySummary(
      entryDate = date,
      totalValue = totalValue.setScale(2, RoundingMode.HALF_UP),
      xirrAnnualReturn = BigDecimal(xirrResult).setScale(8, RoundingMode.HALF_UP),
      totalProfit = totalProfit.setScale(2, RoundingMode.HALF_UP),
      earningsPerDay = totalValue.multiply(BigDecimal(xirrResult))
        .divide(BigDecimal(365.25), 2, RoundingMode.HALF_UP)
    )
  }

  @Transactional
  @Caching(
    evict = [
      CacheEvict(value = [SUMMARY_CACHE], key = "'summaries'"),
      CacheEvict(value = [SUMMARY_CACHE], allEntries = true)
    ]
  )
  fun saveDailySummaries(summaries: List<PortfolioDailySummary>) {
    val existingSummaries = portfolioDailySummaryRepository.findAllByEntryDateIn(
      summaries.map { it.entryDate }
    ).associateBy { it.entryDate }

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
  fun getDailySummariesBetween(startDate: LocalDate, endDate: LocalDate): List<PortfolioDailySummary> {
    return portfolioDailySummaryRepository.findAllByEntryDateBetween(startDate, endDate)
  }
}
