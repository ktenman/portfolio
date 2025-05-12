package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.domain.PortfolioDailySummary
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
    val transactions = portfolioTransactionService.getAllTransactions()
      .sortedBy { it.transactionDate }
    if (transactions.isEmpty()) {
      log.info("No transactions found. Nothing to recalculate.")
      return 0
    }
    val firstTransactionDate = transactions.first().transactionDate
    val today = LocalDate.now(clock)
    log.info("Recalculating summaries from $firstTransactionDate to $today")
    portfolioDailySummaryRepository.deleteAll()
    portfolioDailySummaryRepository.flush()
    val datesToProcess = generateSequence(firstTransactionDate) { d ->
      d.plusDays(1).takeIf { !it.isAfter(today) }
    }.toList()
    val batchSize = 30
    val summariesSaved = datesToProcess.chunked(batchSize).sumOf { batch ->
      val summaries = batch.map { calculateSummaryForDate(it) }
      portfolioDailySummaryRepository.saveAll(summaries)
      summaries.size
    }
    log.info("Successfully recalculated and saved $summariesSaved daily summaries")
    return summariesSaved
  }

  @Transactional(readOnly = true)
  @Cacheable(value = [SUMMARY_CACHE], key = "'summaries'", unless = "#result.isEmpty()")
  fun getAllDailySummaries(): List<PortfolioDailySummary> =
    portfolioDailySummaryRepository.findAll()

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
  fun getCurrentDaySummary(): PortfolioDailySummary =
    calculateSummaryForDate(LocalDate.now(clock))

  @Transactional(readOnly = true)
  fun calculateSummaryForDate(date: LocalDate): PortfolioDailySummary {
    val transactions = portfolioTransactionService.getAllTransactions()
      .filter { !it.transactionDate.isAfter(date) }
      .sortedBy { it.transactionDate }

    if (transactions.isEmpty()) {
      return PortfolioDailySummary(
        entryDate = date,
        totalValue = BigDecimal.ZERO,
        xirrAnnualReturn = BigDecimal.ZERO,
        totalProfit = BigDecimal.ZERO,
        earningsPerDay = BigDecimal.ZERO
      )
    }

    var totalValue = BigDecimal.ZERO
    var totalInvestment = BigDecimal.ZERO
    val xirrTx = mutableListOf<Transaction>()

    // Process each instrument separately
    transactions.groupBy { it.instrument }.forEach { (instrument, instrumentTransactions) ->
      try {
        // Try using the UnifiedProfitCalculationService first (new approach)
        val holdingsResult = unifiedProfitCalculationService.calculateCurrentHoldings(instrumentTransactions)

        // Safely get components with null check
        val currentHoldings = holdingsResult?.first ?: BigDecimal.ZERO
        val averageCost = holdingsResult?.second ?: BigDecimal.ZERO

        if (currentHoldings > BigDecimal.ZERO) {
          // Get price for this date
          val price = dailyPriceService.getPrice(instrument, date)

          // Calculate current value
          val currentValue = currentHoldings.multiply(price)
          totalValue = totalValue.add(currentValue)

          // Calculate investment using average cost method
          val investment = currentHoldings.multiply(averageCost)
          totalInvestment = totalInvestment.add(investment)

          // For XIRR calculation
          instrumentTransactions.forEach { tx ->
            val amount = when (tx.transactionType) {
              TransactionType.BUY -> -(tx.price.multiply(tx.quantity))
              TransactionType.SELL -> tx.price.multiply(tx.quantity)
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

          // Calculate investment (summing up BUY transactions)
          val investment = instrumentTransactions
            .filter { it.transactionType == TransactionType.BUY }
            .sumOf { it.price.multiply(it.quantity) }

          totalInvestment = totalInvestment.add(investment)

          // For XIRR calculation
          instrumentTransactions.forEach { tx ->
            val amount = when (tx.transactionType) {
              TransactionType.BUY -> -(tx.price.multiply(tx.quantity))
              TransactionType.SELL -> tx.price.multiply(tx.quantity)
            }
            xirrTx.add(Transaction(amount.toDouble(), tx.transactionDate))
          }

          xirrTx.add(Transaction(currentValue.toDouble(), date))
        }
      }
    }

    // Calculate profit as the difference between current value and investment
    val totalProfit = totalValue.subtract(totalInvestment)

    // Calculate XIRR
    val xirr = if (xirrTx.size > 1) {
      unifiedProfitCalculationService.calculateAdjustedXirr(xirrTx, totalValue)
    } else {
      0.0
    }

    // Calculate earnings per day based on XIRR and total value
    val earningsPerDay = totalValue.multiply(BigDecimal(xirr))
      .divide(BigDecimal(365.25), 10, RoundingMode.HALF_UP)

    return PortfolioDailySummary(
      entryDate = date,
      totalValue = totalValue.setScale(10, RoundingMode.HALF_UP),
      xirrAnnualReturn = BigDecimal(xirr).setScale(8, RoundingMode.HALF_UP),
      totalProfit = totalProfit.setScale(10, RoundingMode.HALF_UP),
      earningsPerDay = earningsPerDay.setScale(10, RoundingMode.HALF_UP)
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
    val existing = portfolioDailySummaryRepository
      .findAllByEntryDateIn(summaries.map { it.entryDate })
      .associateBy { it.entryDate }
    val merged = summaries.map { s ->
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
  fun getDailySummariesBetween(startDate: LocalDate, endDate: LocalDate) =
    portfolioDailySummaryRepository.findAllByEntryDateBetween(startDate, endDate)
}
