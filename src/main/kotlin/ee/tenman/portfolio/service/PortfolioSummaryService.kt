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

  @Transactional(readOnly = true)
  @Cacheable(value = [SUMMARY_CACHE], key = "'summaries'", unless = "#result.isEmpty()")
  fun getAllDailySummaries(): List<PortfolioDailySummary> = portfolioDailySummaryRepository.findAll()

  @Transactional(readOnly = true)
  @Cacheable(value = [SUMMARY_CACHE], key = "'summaries-page-' + #page + '-size-' + #size", unless = "#result.isEmpty()")
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
    var netInvestment = BigDecimal.ZERO
    var totalValue = BigDecimal.ZERO

    // Process all transactions up to the date
    transactions.forEach { transaction ->
      val amount = transaction.price.multiply(transaction.quantity)
      val transactionValue = when (transaction.transactionType) {
        TransactionType.BUY -> -amount
        TransactionType.SELL -> amount
      }
      xirrTransactions.add(Transaction(transactionValue.toDouble(), transaction.transactionDate))

      when (transaction.transactionType) {
        TransactionType.BUY -> netInvestment = netInvestment.add(amount)
        TransactionType.SELL -> netInvestment = netInvestment.subtract(amount)
      }
    }

    // Calculate current holdings value
    val holdings = calculateHoldings(transactions)
    totalValue = calculateTotalValue(holdings, date)

    // Add current value as the final transaction
    xirrTransactions.add(Transaction(totalValue.toDouble(), date))

    // Calculate XIRR
    val xirrResult = if (xirrTransactions.size > 1) {
      try {
        Xirr(xirrTransactions).calculate()
      } catch (e: Exception) {
        log.error("Error calculating XIRR", e)
        0.0
      }
    } else 0.0

    val profit = totalValue.subtract(netInvestment)
    val xirrAnnual = BigDecimal(xirrResult)

    log.debug("Date: $date, Total Value: $totalValue, Net Investment: $netInvestment, XIRR: $xirrResult")

    return PortfolioDailySummary(
      entryDate = date,
      totalValue = totalValue.setScale(2, RoundingMode.HALF_UP),
      xirrAnnualReturn = xirrAnnual.setScale(8, RoundingMode.HALF_UP),
      totalProfit = profit.setScale(2, RoundingMode.HALF_UP),
      earningsPerDay = totalValue.multiply(xirrAnnual)
        .divide(BigDecimal(365.25), 2, RoundingMode.HALF_UP)
    )
  }

  private fun calculateHoldings(transactions: List<PortfolioTransaction>): Map<Instrument, BigDecimal> {
    val holdings = mutableMapOf<Instrument, BigDecimal>()

    transactions.forEach { transaction ->
      val currentHolding = holdings.getOrDefault(transaction.instrument, BigDecimal.ZERO)
      holdings[transaction.instrument] = when (transaction.transactionType) {
        TransactionType.BUY -> currentHolding.add(transaction.quantity)
        TransactionType.SELL -> currentHolding.subtract(transaction.quantity)
      }
    }

    return holdings.filterValues { it > BigDecimal.ZERO }
  }

  private fun calculateTotalValue(
    holdings: Map<Instrument, BigDecimal>,
    date: LocalDate
  ): BigDecimal {
    return holdings.entries.sumOf { (instrument, quantity) ->
      val lastPrice = dailyPriceService.findLastDailyPrice(instrument, date)?.closePrice
        ?: throw IllegalStateException("No price found for instrument: ${instrument.symbol} on or before $date")
      quantity.multiply(lastPrice)
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
  fun getDailySummary(date: LocalDate): PortfolioDailySummary? {
    return portfolioDailySummaryRepository.findByEntryDate(date)
  }
}
