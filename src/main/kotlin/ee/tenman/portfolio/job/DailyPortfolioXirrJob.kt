package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.service.DailyPriceService
import ee.tenman.portfolio.service.JobExecutionService
import ee.tenman.portfolio.service.PortfolioSummaryService
import ee.tenman.portfolio.service.PortfolioTransactionService
import ee.tenman.portfolio.service.xirr.Transaction
import ee.tenman.portfolio.service.xirr.Xirr
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.CompletableFuture

@Component
class DailyPortfolioXirrJob(
  private val portfolioTransactionService: PortfolioTransactionService,
  private val portfolioSummaryService: PortfolioSummaryService,
  private val dailyPriceService: DailyPriceService,
  private val clock: Clock,
  private val jobExecutionService: JobExecutionService
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "0 30 6 * * *")
  fun runJob() {
    log.info("Running daily portfolio XIRR job")
    jobExecutionService.executeJob(this)
    log.info("Completed daily portfolio XIRR job")
  }

  override fun execute() {
    log.info("Starting daily portfolio XIRR calculation")
    val allTransactions = portfolioTransactionService.getAllTransactions()
      .sortedBy { it.transactionDate }

    if (allTransactions.isEmpty()) {
      log.info("No transactions found. Skipping XIRR calculation.")
      return
    }

    val firstTransactionDate = allTransactions.first().transactionDate
    val yesterday = LocalDate.now(clock).minusDays(1)

    log.info("Calculating summaries from $firstTransactionDate to $yesterday")

    try {
      val summariesToSave = mutableListOf<PortfolioDailySummary>()
      var currentDate = firstTransactionDate

      while (!currentDate.isAfter(yesterday)) {
        val existingSummary = portfolioSummaryService.getDailySummary(currentDate)
        if (existingSummary == null) {
          val relevantTransactions = allTransactions.filter { !it.transactionDate.isAfter(currentDate) }
          log.info("Calculating summary for date: $currentDate with ${relevantTransactions.size} transactions")
          val summary = calculateSummaryForDate(relevantTransactions, currentDate)
          summariesToSave.add(summary)
          log.info("Calculated summary for date: $currentDate. XIRR: ${summary.xirrAnnualReturn}")
        }
        currentDate = currentDate.plusDays(1)
      }

      if (summariesToSave.isNotEmpty()) {
        portfolioSummaryService.saveDailySummaries(summariesToSave)
        log.info("Saved ${summariesToSave.size} new daily summaries.")
      }

    } catch (e: Exception) {
      log.error("Error calculating XIRR", e)
      throw e
    }
  }

  private fun calculateSummaryForDate(
    transactions: List<PortfolioTransaction>,
    date: LocalDate
  ): PortfolioDailySummary {
    val instrumentHoldings = calculateHoldings(transactions)
    val currentValue = calculateCurrentValue(instrumentHoldings, date)
    val totalInvestment = calculateTotalInvestment(transactions, instrumentHoldings)
    val xirrResult = calculateXirr(transactions, currentValue, date, instrumentHoldings)

    return createDailySummary(totalInvestment, currentValue, xirrResult, date)
  }

  private fun calculateHoldings(transactions: List<PortfolioTransaction>): Map<Instrument, BigDecimal> {
    val holdings = mutableMapOf<Instrument, BigDecimal>()
    transactions.forEach { transaction ->
      val currentHolding = holdings.getOrDefault(transaction.instrument, BigDecimal.ZERO)
      holdings[transaction.instrument] = when (transaction.transactionType) {
        TransactionType.BUY -> currentHolding + transaction.quantity
        TransactionType.SELL -> currentHolding - transaction.quantity
      }
    }
    return holdings.filterValues { it > BigDecimal.ZERO }
  }

  private fun calculateCurrentValue(
    holdings: Map<Instrument, BigDecimal>,
    date: LocalDate
  ): BigDecimal {
    return holdings.entries.sumOf { (instrument, quantity) ->
      val lastPrice = dailyPriceService.findLastDailyPrice(instrument, date)?.closePrice
        ?: throw IllegalStateException("No price found for instrument: ${instrument.symbol} on or before $date")
      quantity * lastPrice
    }
  }

  private fun calculateTotalInvestment(
    transactions: List<PortfolioTransaction>,
    activeHoldings: Map<Instrument, BigDecimal>
  ): BigDecimal {
    return transactions
      .filter { activeHoldings.containsKey(it.instrument) }
      .filter { it.transactionType == TransactionType.BUY }
      .sumOf { it.quantity * it.price }
  }

  private fun calculateXirr(
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal,
    date: LocalDate,
    activeHoldings: Map<Instrument, BigDecimal>
  ): Double {
    val xirrTransactions = transactions
      .filter { activeHoldings.containsKey(it.instrument) }.mapNotNull { transaction ->
        when (transaction.transactionType) {
          TransactionType.BUY -> Transaction(
            -(transaction.price * transaction.quantity).toDouble(),
            transaction.transactionDate
          )

          TransactionType.SELL -> null
        }
      }
      .toMutableList()

    xirrTransactions.add(Transaction(currentValue.toDouble(), date))

    return try {
      if (xirrTransactions.size < 2) return 0.0
      Xirr(xirrTransactions).calculate()
    } catch (e: Exception) {
      log.error("Error calculating XIRR for date $date", e)
      0.0
    }
  }

  private fun createDailySummary(
    totalInvestment: BigDecimal,
    currentValue: BigDecimal,
    xirrResult: Double,
    date: LocalDate
  ): PortfolioDailySummary {
    return PortfolioDailySummary(
      entryDate = date,
      totalValue = currentValue.setScale(4, RoundingMode.HALF_UP),
      xirrAnnualReturn = BigDecimal(xirrResult).setScale(8, RoundingMode.HALF_UP),
      totalProfit = currentValue.subtract(totalInvestment).setScale(4, RoundingMode.HALF_UP),
      earningsPerDay = currentValue.multiply(BigDecimal(xirrResult))
        .divide(BigDecimal(365.25), 4, RoundingMode.HALF_UP)
    )
  }

  override fun getName(): String = "DailyPortfolioXirrJob"
}
