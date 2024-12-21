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
      val existingDates = portfolioSummaryService.getDailySummariesBetween(firstTransactionDate, yesterday)
        .map { it.entryDate }
        .toSet()

      val datesToProcess = generateSequence(firstTransactionDate) { date ->
        val next = date.plusDays(1)
        if (next.isAfter(yesterday)) null else next
      }.filterNot { it in existingDates }
        .toList()

      val summariesToSave = datesToProcess.map { currentDate ->
        log.info("Calculating summary for date: $currentDate")
        val summary = portfolioSummaryService.calculateSummaryForDate(currentDate)
        log.info("Calculated summary for date: $currentDate. XIRR: ${summary.xirrAnnualReturn}")
        summary
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
}
