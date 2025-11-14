package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.CalculationService
import ee.tenman.portfolio.service.JobExecutionService
import ee.tenman.portfolio.service.SummaryService
import ee.tenman.portfolio.service.TransactionService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

@Component
@ConditionalOnProperty(name = ["scheduling.enabled"], havingValue = "true", matchIfMissing = true)
class DailyPortfolioXirrJob(
  private val portfolioTransactionService: TransactionService,
  private val portfolioSummaryService: SummaryService,
  private val calculationService: CalculationService,
  private val clock: Clock,
  private val jobExecutionService: JobExecutionService,
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
    val allTransactions =
      portfolioTransactionService
        .getAllTransactions()
        .sortedWith(compareBy({ it.transactionDate }, { it.id }))

    if (allTransactions.isEmpty()) {
      log.info("No transactions found. Skipping XIRR calculation.")
      return
    }

    val firstTransactionDate = allTransactions.first().transactionDate
    val yesterday = LocalDate.now(clock).minusDays(1)

    log.info("Calculating summaries from $firstTransactionDate to $yesterday")

    try {
      val existingDates =
        portfolioSummaryService
          .getDailySummariesBetween(firstTransactionDate, yesterday)
          .map { it.entryDate }
          .toSet()

      val datesToProcess =
        generateSequence(firstTransactionDate) { date ->
          val next = date.plusDays(1)
          if (next.isAfter(yesterday)) null else next
        }.filterNot { it in existingDates }
          .toList()

      if (datesToProcess.isNotEmpty()) {
        log.info("Processing ${datesToProcess.size} dates in parallel batches")

        val result =
          runBlocking {
            calculationService.calculateBatchXirrAsync(datesToProcess)
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
