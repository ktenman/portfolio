package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.service.DailyPriceService
import ee.tenman.portfolio.service.JobExecutionService
import ee.tenman.portfolio.service.PortfolioSummaryService
import ee.tenman.portfolio.service.PortfolioTransactionService
import ee.tenman.portfolio.service.xirr.Transaction
import ee.tenman.portfolio.service.xirr.Xirr
import ee.tenman.portfolio.service.xirr.XirrService
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
  private val jobExecutionService: JobExecutionService,
  private val xirrService: XirrService
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "0 0 9 * * *")
  fun runJob() {
    log.info("Running daily portfolio XIRR job")
    jobExecutionService.executeJob(this)
    log.info("Completed daily portfolio XIRR job")
  }

  override fun execute() {
    log.info("Starting daily portfolio XIRR calculation")
    val allTransactions = portfolioTransactionService.getAllTransactions().sortedBy { it.transactionDate }

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
        } else {
          log.info("Summary already exists for date: $currentDate. Skipping calculation.")
        }
        currentDate = currentDate.plusDays(1)
      }

      if (summariesToSave.isNotEmpty()) {
        portfolioSummaryService.saveDailySummaries(summariesToSave)
        log.info("Saved ${summariesToSave.size} new daily summaries.")
      } else {
        log.info("No new summaries to save.")
      }

    } catch (e: Exception) {
      log.error("Error calculating XIRR", e)
    }
  }

  private fun calculateSummaryForDate(
    transactions: List<PortfolioTransaction>,
    date: LocalDate
  ): PortfolioDailySummary {
    val (totalInvestment, currentValue) = processTransactions(transactions, date)
    log.info("For date $date: Total Investment = $totalInvestment, Current Value = $currentValue")
    val xirrResult = calculateXirr(transactions, currentValue, date)
    return createDailySummary(totalInvestment, currentValue, xirrResult, date)
  }

  private fun processTransactions(
    transactions: List<PortfolioTransaction>,
    latestDate: LocalDate
  ): Pair<BigDecimal, BigDecimal> {
    val (totalInvestment, holdings) = xirrService.calculateInvestmentAndHoldings(transactions)
    val currentValue = calculateCurrentValue(holdings, latestDate)
    return Pair(totalInvestment, currentValue)
  }

  private fun calculateCurrentValue(holdings: Map<Instrument, BigDecimal>, latestDate: LocalDate): BigDecimal {
    return holdings.entries.sumOf { (instrument, quantity) ->
      val lastPrice = dailyPriceService.findLastDailyPrice(instrument, latestDate)?.closePrice
        ?: throw IllegalStateException("No price found for instrument: ${instrument.symbol} on or before $latestDate")
      quantity * lastPrice
    }
  }

  private fun calculateXirr(
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal,
    date: LocalDate
  ): Double {
    val xirrTransactions = transactions.map { transaction ->
      Transaction(-transaction.price.multiply(transaction.quantity).toDouble(), transaction.transactionDate)
    }
    val finalTransaction = Transaction(currentValue.toDouble(), date)
    log.info("Calculating XIRR for date $date with ${xirrTransactions.size + 1} transactions")
    xirrTransactions.forEach { log.info("XIRR Transaction: Amount = ${it.amount}, Date = ${it.date}") }
    log.info("Final XIRR Transaction: Amount = ${finalTransaction.amount}, Date = ${finalTransaction.date}")
    return Xirr(xirrTransactions + finalTransaction).calculate()
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
