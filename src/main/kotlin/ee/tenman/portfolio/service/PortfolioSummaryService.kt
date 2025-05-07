package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import ee.tenman.portfolio.service.xirr.Transaction
import ee.tenman.portfolio.service.xirr.Xirr
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
  @Cacheable(value = [SUMMARY_CACHE], key = "'currentDaySummary'")
  fun getCurrentDaySummary(): PortfolioDailySummary {
    val currentDate = LocalDate.now(clock)
    return calculateSummaryForDate(currentDate)
  }

  @Transactional(readOnly = true)
  fun calculateSummaryForDate(date: LocalDate): PortfolioDailySummary {
    val transactions = portfolioTransactionService.getAllTransactions()
      .filter { !it.transactionDate.isAfter(date) }
      .sortedBy { it.transactionDate }

    val xirrTransactions = mutableListOf<Transaction>()
    val holdings = calculateHoldings(transactions)
    val totalValue = calculateTotalValue(holdings, date)

    // Group transactions by instrument for accurate profit calculation
    val transactionsByInstrument = transactions.groupBy { it.instrument }
    var totalInvestment = BigDecimal.ZERO
    var totalProfit = BigDecimal.ZERO

    transactionsByInstrument.forEach { (instrument, instrumentTransactions) ->
      val instrumentHoldings = calculateCurrentHoldings(instrumentTransactions)
      val lastPrice = dailyPriceService.findLastDailyPrice(instrument, date)?.closePrice
        ?: throw IllegalStateException("No price found for instrument: ${instrument.symbol} on or before $date")

      if (instrumentHoldings.quantity > BigDecimal.ZERO) {
        // Add current value to totalInvestment using average cost basis
        totalInvestment = totalInvestment.add(
          instrumentHoldings.quantity.multiply(instrumentHoldings.averageCost)
        )

        // Calculate profit for current holdings
        val currentValue = instrumentHoldings.quantity.multiply(lastPrice)
        val profitForInstrument = currentValue.subtract(
          instrumentHoldings.quantity.multiply(instrumentHoldings.averageCost)
        )
        totalProfit = totalProfit.add(profitForInstrument)

        // Add XIRR transactions
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

        // Add current value for XIRR calculation
        xirrTransactions.add(Transaction(currentValue.toDouble(), date))
      }
    }

    val xirrResult = if (xirrTransactions.size > 1) {
      try {
        Xirr(xirrTransactions).calculate()
      } catch (e: Exception) {
        log.error("Error calculating XIRR", e)
        0.0
      }
    } else 0.0

    log.debug("Date: $date, Total Value: $totalValue, Total Investment: $totalInvestment")
    log.info("Total profit: $totalProfit")

    return PortfolioDailySummary(
      entryDate = date,
      totalValue = totalValue.setScale(2, RoundingMode.HALF_UP),
      xirrAnnualReturn = BigDecimal(xirrResult).setScale(8, RoundingMode.HALF_UP),
      totalProfit = totalProfit.setScale(2, RoundingMode.HALF_UP),
      earningsPerDay = totalValue.multiply(BigDecimal(xirrResult))
        .divide(BigDecimal(365.25), 2, RoundingMode.HALF_UP)
    )
  }

  private data class Holdings(
    val quantity: BigDecimal,
    val averageCost: BigDecimal
  )

  private fun calculateCurrentHoldings(transactions: List<PortfolioTransaction>): Holdings {
    var quantity = BigDecimal.ZERO
    var totalCost = BigDecimal.ZERO

    transactions.sortedBy { it.transactionDate }.forEach { transaction ->
      when (transaction.transactionType) {
        TransactionType.BUY -> {
          val cost = transaction.price.multiply(transaction.quantity)
          totalCost = totalCost.add(cost)
          quantity = quantity.add(transaction.quantity)
        }

        TransactionType.SELL -> {
          // When selling, reduce the quantity and proportionally reduce the total cost
          val sellRatio = transaction.quantity.divide(quantity, 10, RoundingMode.HALF_UP)
          totalCost = totalCost.multiply(BigDecimal.ONE.subtract(sellRatio))
          quantity = quantity.subtract(transaction.quantity)
        }
      }
    }

    val averageCost = if (quantity > BigDecimal.ZERO) {
      totalCost.divide(quantity, 10, RoundingMode.HALF_UP)
    } else {
      BigDecimal.ZERO
    }

    return Holdings(quantity, averageCost)
  }

  private fun calculateHoldings(transactions: List<PortfolioTransaction>): Map<Instrument, BigDecimal> {
    val holdings = mutableMapOf<Instrument, BigDecimal>()

    transactions.forEach { transaction ->
      holdings[transaction.instrument] =
        holdings.getOrDefault(transaction.instrument, BigDecimal.ZERO).let { currentHolding ->
          when (transaction.transactionType) {
            TransactionType.BUY -> currentHolding.add(transaction.quantity)
            TransactionType.SELL -> currentHolding.subtract(transaction.quantity)
          }
        }
    }

    return holdings.filterValues { it > BigDecimal.ZERO }
  }

  private fun calculateTotalValue(
    holdings: Map<Instrument, BigDecimal>,
    date: LocalDate
  ): BigDecimal {
    val isCurrentDay = date == LocalDate.now(clock)

    return holdings.entries.sumOf { (instrument, quantity) ->
      val price = if (isCurrentDay && instrument.currentPrice != null) {
        instrument.currentPrice
      } else {
        dailyPriceService.findLastDailyPrice(instrument, date)?.closePrice
          ?: throw IllegalStateException("No price found for instrument: ${instrument.symbol} on or before $date")
      }
      quantity.multiply(price)
    }
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
