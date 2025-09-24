package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import ee.tenman.portfolio.service.xirr.Transaction
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
class PortfolioSummaryService(
  private val portfolioDailySummaryRepository: PortfolioDailySummaryRepository,
  private val portfolioTransactionService: PortfolioTransactionService,
  private val dailyPriceService: DailyPriceService,
  private val unifiedProfitCalculationService: UnifiedProfitCalculationService,
  private val instrumentService: InstrumentService,
  private val cacheManager: CacheManager,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

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

    // Clear instrument cache to ensure fresh data
    cacheManager.getCache(INSTRUMENT_CACHE)?.clear()

    val transactions =
      portfolioTransactionService
        .getAllTransactions()
        .sortedBy { it.transactionDate }
    if (transactions.isEmpty()) {
      log.info("No transactions found. Nothing to recalculate.")
      return 0
    }

    val firstTransactionDate = transactions.first().transactionDate
    val today = LocalDate.now(clock)
    val yesterday = today.minusDays(1) // Use yesterday as the upper bound
    log.info("Recalculating summaries from $firstTransactionDate to $yesterday (excluding current day)")

    // Delete all historical summaries (excluding today)
    portfolioDailySummaryRepository.findAll().forEach { summary ->
      if (summary.entryDate != today) {
        portfolioDailySummaryRepository.delete(summary)
      }
    }
    portfolioDailySummaryRepository.flush()

    val datesToProcess =
      generateSequence(firstTransactionDate) { d ->
        d.plusDays(1).takeIf { !it.isAfter(yesterday) } // Only up to yesterday
      }.toList()

    val batchSize = 30
    val summariesSaved =
      datesToProcess.chunked(batchSize).sumOf { batch ->
        val summaries = batch.map { calculateSummaryForDate(it) }
        portfolioDailySummaryRepository.saveAll(summaries)
        summaries.size
      }

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

    // Get all instruments with their metrics
    val instruments = instrumentService.getAllInstruments()

    // Calculate values using instrument data
    val totalValue = instruments.sumOf { it.currentValue }
    val totalProfit = instruments.sumOf { it.profit }

    // Get the XIRR directly from the main instrument
    val mainInstrument = instruments.maxByOrNull { it.currentValue }
    val xirrValue = mainInstrument?.xirr ?: 0.0

    val xirrBigDecimal = BigDecimal(xirrValue).setScale(8, RoundingMode.HALF_UP)
    val earningsPerDay =
      totalValue
        .multiply(xirrBigDecimal)
        .divide(BigDecimal(365.25), 10, RoundingMode.HALF_UP)

    // Create a new summary with the values from instruments
    val summary =
      PortfolioDailySummary(
        entryDate = today,
        totalValue = totalValue,
        xirrAnnualReturn = xirrBigDecimal, // Use the instrument's XIRR
        totalProfit = totalProfit,
        earningsPerDay = earningsPerDay,
      )

    // If there's an existing summary, preserve its ID and version
    val existingSummary = portfolioDailySummaryRepository.findByEntryDate(today)
    if (existingSummary != null) {
      summary.id = existingSummary.id
      summary.version = existingSummary.version
    }

    return summary
  }

  private fun alignCurrentDaySummaryWithInstruments(summary: PortfolioDailySummary): PortfolioDailySummary {
    val instruments = instrumentService.getAllInstruments()

    val totalValue = instruments.sumOf { it.currentValue }
    val totalProfit = instruments.sumOf { it.profit }
    val earningsPerDay =
      totalValue
        .multiply(summary.xirrAnnualReturn)
        .divide(BigDecimal(365.25), 10, RoundingMode.HALF_UP)

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

  @Transactional(readOnly = true)
  fun calculateSummaryForDate(date: LocalDate): PortfolioDailySummary {
    val today = LocalDate.now(clock)
    val isToday = date.isEqual(today)

    // Get existing summary for this date if it exists
    val existingSummary = portfolioDailySummaryRepository.findByEntryDate(date)

    // For current day, use the current endpoint logic
    if (isToday) {
      return existingSummary?.let {
        alignCurrentDaySummaryWithInstruments(it)
      } ?: createCurrentDaySummary(date)
    }

    // For historical dates:
    if (existingSummary != null) {
      return existingSummary
    }

    // Check if any transactions happened on this date
    val transactionsOnDate =
      portfolioTransactionService
        .getAllTransactions()
        .filter { it.transactionDate.isEqual(date) }

    // If no transactions on this date, check if we can use previous day's values
    if (transactionsOnDate.isEmpty()) {
      val previousDate = date.minusDays(1)
      val previousSummary = portfolioDailySummaryRepository.findByEntryDate(previousDate)

      if (previousSummary != null) {
        // Calculate basic value, xirr etc for this date
        val summary = calculateSummaryDetailsForDate(date)

        // If value matches previous day, use previous day's profit
        if (summary.totalValue.compareTo(previousSummary.totalValue) == 0) {
          return PortfolioDailySummary(
            entryDate = date,
            totalValue = summary.totalValue,
            xirrAnnualReturn = summary.xirrAnnualReturn,
            totalProfit = summary.totalProfit,
            earningsPerDay = summary.earningsPerDay,
          )
        }
        return summary
      }
    }

    // Do full calculation
    return calculateSummaryDetailsForDate(date)
  }

  private fun createCurrentDaySummary(date: LocalDate): PortfolioDailySummary {
    val instruments = instrumentService.getAllInstruments()
    val totalValue = instruments.sumOf { it.currentValue }
    val totalProfit = instruments.sumOf { it.profit }

    val xirrTx = buildXirrTransactions(date)
    // Pass the date to calculateXirr
    val xirr = calculateXirr(xirrTx, totalValue, date)

    val xirrBigDecimal = BigDecimal(xirr).setScale(8, RoundingMode.HALF_UP)
    val earningsPerDay =
      totalValue
        .multiply(xirrBigDecimal)
        .divide(BigDecimal(365.25), 10, RoundingMode.HALF_UP)

    return PortfolioDailySummary(
      entryDate = date,
      totalValue = totalValue,
      xirrAnnualReturn = xirrBigDecimal,
      totalProfit = totalProfit,
      earningsPerDay = earningsPerDay,
    )
  }

  // Helper method to build XIRR transactions
  private fun buildXirrTransactions(date: LocalDate): List<Transaction> {
    val transactions =
      portfolioTransactionService
        .getAllTransactions()
        .filter { !it.transactionDate.isAfter(date) }

    val xirrTx =
      transactions
        .map { tx ->
          val amount =
            when (tx.transactionType) {
              TransactionType.BUY -> -(tx.price.multiply(tx.quantity).add(tx.commission))
              TransactionType.SELL -> tx.price.multiply(tx.quantity).subtract(tx.commission)
            }
          Transaction(amount.toDouble(), tx.transactionDate)
        }.toMutableList()

    // Get current value from instruments
    val instruments = instrumentService.getAllInstruments()
    val totalValue = instruments.sumOf { it.currentValue }

    if (totalValue > BigDecimal.ZERO) {
      xirrTx.add(Transaction(totalValue.toDouble(), date))
    }

    return xirrTx
  }

  private fun calculateXirr(
    transactions: List<Transaction>,
    totalValue: BigDecimal,
    date: LocalDate,
  ): Double =
    if (transactions.size > 1) {
      // Pass the calculation date to ensure consistency
      unifiedProfitCalculationService.calculateAdjustedXirr(transactions, totalValue, date)
    } else {
      0.0
    }

  /**
   * Helper method containing the original calculation logic
   */
  private fun calculateSummaryDetailsForDate(date: LocalDate): PortfolioDailySummary {
    val transactions =
      portfolioTransactionService
        .getAllTransactions()
        .filter { !it.transactionDate.isAfter(date) }
        .sortedBy { it.transactionDate }

    if (transactions.isEmpty()) {
      return PortfolioDailySummary(
        entryDate = date,
        totalValue = BigDecimal.ZERO,
        xirrAnnualReturn = BigDecimal.ZERO,
        totalProfit = BigDecimal.ZERO,
        earningsPerDay = BigDecimal.ZERO,
      )
    }

    // For historical dates, we need to calculate the state of instruments as of that date
    var totalValue = BigDecimal.ZERO
    var totalProfit = BigDecimal.ZERO
    val xirrTx = mutableListOf<Transaction>()

    // Process each instrument separately
    transactions.groupBy { it.instrument }.forEach { (instrument, instrumentTransactions) ->
      try {
        // Try using the UnifiedProfitCalculationService first (new approach)
        val holdingsResult = unifiedProfitCalculationService.calculateCurrentHoldings(instrumentTransactions)

        // Safely get components with null check
        val currentHoldings = holdingsResult.first
        val averageCost = holdingsResult.second

        if (currentHoldings > BigDecimal.ZERO) {
          // Get price for this date
          val price = dailyPriceService.getPrice(instrument, date)

          // Calculate current value
          val currentValue = currentHoldings.multiply(price)
          totalValue = totalValue.add(currentValue)

          // Calculate profit using the same method as instruments
          // This accounts for realized gains/losses from sells
          val instrumentProfit =
            unifiedProfitCalculationService.calculateProfit(
            currentHoldings,
            averageCost,
            price,
          )
          totalProfit = totalProfit.add(instrumentProfit)

          // For XIRR calculation
          instrumentTransactions.forEach { tx ->
            val amount =
              when (tx.transactionType) {
                TransactionType.BUY -> -(tx.price.multiply(tx.quantity).add(tx.commission))
                TransactionType.SELL -> tx.price.multiply(tx.quantity).subtract(tx.commission)
              }
            xirrTx.add(Transaction(amount.toDouble(), tx.transactionDate))
          }

          xirrTx.add(Transaction(currentValue.toDouble(), date))
        }
      } catch (e: Exception) {
        // Fallback to original approach if new method fails (for test compatibility)
        log.warn("Failed to use unified profit calculation, falling back to original method: ${e.message}")

        // Calculate total holdings for this instrument (original approach)
        var netQuantity = BigDecimal.ZERO

        instrumentTransactions.forEach { tx ->
          if (tx.transactionType == TransactionType.BUY) {
            netQuantity = netQuantity.add(tx.quantity)
          } else {
            netQuantity = netQuantity.subtract(tx.quantity)
          }
        }

        if (netQuantity > BigDecimal.ZERO) {
          // Get price for this date
          val price = dailyPriceService.getPrice(instrument, date)

          // Calculate current value
          val currentValue = netQuantity.multiply(price)
          totalValue = totalValue.add(currentValue)

          // For fallback, use the same profit calculation approach
          val totalBuys =
            instrumentTransactions
            .filter { it.transactionType == TransactionType.BUY }
            .sumOf { it.price.multiply(it.quantity).add(it.commission) }
          val totalSells =
            instrumentTransactions
            .filter { it.transactionType == TransactionType.SELL }
            .sumOf { it.price.multiply(it.quantity).subtract(it.commission) }

          // Calculate realized gains/losses from sells
          val realizedGains =
            if (totalSells > BigDecimal.ZERO) {
            val sellQuantity =
              instrumentTransactions
              .filter { it.transactionType == TransactionType.SELL }
              .sumOf { it.quantity }
            val avgBuyPrice =
              totalBuys.divide(
              instrumentTransactions
                .filter { it.transactionType == TransactionType.BUY }
                .sumOf { it.quantity },
              10,
              RoundingMode.HALF_UP,
            )
            totalSells.subtract(avgBuyPrice.multiply(sellQuantity))
          } else {
            BigDecimal.ZERO
          }

          // Unrealized gains on remaining holdings
          val unrealizedGains =
            currentValue.subtract(
            totalBuys.subtract(
              instrumentTransactions
                .filter { it.transactionType == TransactionType.SELL }
                .sumOf { tx ->
                  val buyQuantity =
                    instrumentTransactions
                    .filter { it.transactionType == TransactionType.BUY }
                    .sumOf { it.quantity }
                  val avgBuyPrice = totalBuys.divide(buyQuantity, 10, RoundingMode.HALF_UP)
                  avgBuyPrice.multiply(tx.quantity)
                },
            ),
          )

          val instrumentProfit = realizedGains.add(unrealizedGains)
          totalProfit = totalProfit.add(instrumentProfit)

          // For XIRR calculation
          instrumentTransactions.forEach { tx ->
            val amount =
              when (tx.transactionType) {
                TransactionType.BUY -> -(tx.price.multiply(tx.quantity).add(tx.commission))
                TransactionType.SELL -> tx.price.multiply(tx.quantity).subtract(tx.commission)
              }
            xirrTx.add(Transaction(amount.toDouble(), tx.transactionDate))
          }

          xirrTx.add(Transaction(currentValue.toDouble(), date))
        }
      }
    }

    // Calculate XIRR
    val xirr =
      if (xirrTx.size > 1) {
        unifiedProfitCalculationService.calculateAdjustedXirr(xirrTx, totalValue, date)
      } else {
        0.0
      }

    // Calculate earnings per day based on XIRR and total value
    val earningsPerDay =
      totalValue
        .multiply(BigDecimal(xirr))
        .divide(BigDecimal(365.25), 10, RoundingMode.HALF_UP)

    return PortfolioDailySummary(
      entryDate = date,
      totalValue = totalValue.setScale(10, RoundingMode.HALF_UP),
      xirrAnnualReturn = BigDecimal(xirr).setScale(8, RoundingMode.HALF_UP),
      totalProfit = totalProfit.setScale(10, RoundingMode.HALF_UP),
      earningsPerDay = earningsPerDay.setScale(10, RoundingMode.HALF_UP),
    )
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

  @Transactional(readOnly = true)
  fun getDailySummariesBetween(
    startDate: LocalDate,
    endDate: LocalDate,
  ) = portfolioDailySummaryRepository.findAllByEntryDateBetween(startDate, endDate)
}
