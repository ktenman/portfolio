package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.AsyncXirrCalculationService
import ee.tenman.portfolio.service.JobExecutionService
import ee.tenman.portfolio.service.PortfolioSummaryService
import ee.tenman.portfolio.service.PortfolioTransactionService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

@Component
class DailyPortfolioXirrJob(
  private val portfolioTransactionService: PortfolioTransactionService,
  private val portfolioSummaryService: PortfolioSummaryService,
  private val asyncXirrCalculationService: AsyncXirrCalculationService,
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

      if (datesToProcess.isNotEmpty()) {
        log.info("Processing ${datesToProcess.size} dates in parallel batches")
        
        val result = runBlocking {
          asyncXirrCalculationService.calculateBatchXirrAsync(datesToProcess)
        }
        
        log.info("Processed ${result.processedDates} dates in ${result.duration}ms")
        if (result.failedCalculations.isNotEmpty()) {
          log.warn("Failed calculations: ${result.failedCalculations}")
        }
      } else {
        log.info("No new dates to process")
      }

    } catch (e: Exception) {
      log.error("Error calculating XIRR", e)
      throw e
    }
  }
}
