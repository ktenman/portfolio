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

@Component
class DailyPortfolioXirrJob(
  private val portfolioTransactionService: PortfolioTransactionService,
  private val portfolioSummaryService: PortfolioSummaryService,
  private val dailyPriceService: DailyPriceService,
  private val clock: Clock,
  private val jobExecutionService: JobExecutionService
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "0/5 * * * * *")
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
    val instrumentGroups = transactions.groupBy { it.instrument }
    var totalValue = BigDecimal.ZERO
    val xirrTransactions = mutableListOf<Transaction>()

    instrumentGroups.forEach { (instrument, instrumentTransactions) ->
      val (instrumentValue, instrumentXirrTransactions) = calculateInstrumentMetrics(
        instrument,
        instrumentTransactions,
        date
      )
      totalValue += instrumentValue
      xirrTransactions.addAll(instrumentXirrTransactions)
    }

    val xirrResult = if (xirrTransactions.size >= 2) {
      try {
        Xirr(xirrTransactions).calculate()
      } catch (e: Exception) {
        log.error("Error calculating XIRR for date $date", e)
        0.0
      }
    } else 0.0

    val totalInvestment = calculateTotalInvestment(transactions)
    val profit = totalValue - totalInvestment

    return PortfolioDailySummary(
      entryDate = date,
      totalValue = totalValue.setScale(4, RoundingMode.HALF_UP),
      xirrAnnualReturn = BigDecimal(xirrResult).setScale(8, RoundingMode.HALF_UP),
      totalProfit = profit.setScale(4, RoundingMode.HALF_UP),
      earningsPerDay = totalValue.multiply(BigDecimal(xirrResult))
        .divide(BigDecimal(365.25), 4, RoundingMode.HALF_UP)
    )
  }

  private fun calculateInstrumentMetrics(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
    date: LocalDate
  ): Pair<BigDecimal, List<Transaction>> {
    var remainingShares = BigDecimal.ZERO
    val xirrTransactions = mutableListOf<Transaction>()

    transactions.sortedBy { it.transactionDate }.forEach { transaction ->
      val transactionAmount = transaction.price.multiply(transaction.quantity)
      when (transaction.transactionType) {
        TransactionType.BUY -> {
          remainingShares += transaction.quantity
          xirrTransactions.add(Transaction(-transactionAmount.toDouble(), transaction.transactionDate))
        }
        TransactionType.SELL -> {
          remainingShares -= transaction.quantity
          xirrTransactions.add(Transaction(transactionAmount.toDouble(), transaction.transactionDate))
        }
      }
    }

    val lastPrice = dailyPriceService.findLastDailyPrice(instrument, date)?.closePrice
      ?: throw IllegalStateException("No price found for instrument: ${instrument.symbol} on or before $date")

    val currentValue = remainingShares.multiply(lastPrice)

    // Only add current value to XIRR calculation if we have remaining shares
    if (remainingShares > BigDecimal.ZERO) {
      xirrTransactions.add(Transaction(currentValue.toDouble(), date))
    }

    return Pair(currentValue, xirrTransactions)
  }

  private fun calculateTotalInvestment(transactions: List<PortfolioTransaction>): BigDecimal {
    return transactions.fold(BigDecimal.ZERO) { acc, transaction ->
      when (transaction.transactionType) {
        TransactionType.BUY -> acc + (transaction.price * transaction.quantity)
        TransactionType.SELL -> acc - (transaction.price * transaction.quantity)
      }
    }
  }

  override fun getName(): String = this::class.simpleName!!
}
