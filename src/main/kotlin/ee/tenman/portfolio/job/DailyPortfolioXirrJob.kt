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
  private val transactionService: TransactionService,
  private val summaryService: SummaryService,
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
    val sorted = transactionService.getAllTransactions().sortedWith(compareBy({ it.transactionDate }, { it.id }))
    if (sorted.isEmpty()) {
      log.info("No transactions found. Skipping XIRR calculation.")
      return
    }

    val start = sorted.first().transactionDate
    val yesterday = LocalDate.now(clock).minusDays(1)
    log.info("Calculating summaries from $start to $yesterday")

    runCatching { process(start, yesterday) }
      .onFailure { log.error("Error calculating XIRR", it); throw it }
  }

  private fun process(start: LocalDate, end: LocalDate) {
    val existing = summaryService.getDailySummariesBetween(start, end).map { it.entryDate }.toSet()
    val dates = generateSequence(start) { it.plusDays(1).takeIf { d -> !d.isAfter(end) } }
      .filterNot { it in existing }
      .toList()

    if (dates.isEmpty()) {
      log.info("No new dates to process")
      return
    }

    log.info("Processing ${dates.size} dates in parallel batches")
    val result = runBlocking { calculationService.batch(dates) }
    log.info("Processed ${result.dates} dates in ${result.duration}ms")
    if (result.failures.isNotEmpty()) log.warn("Failed calculations: ${result.failures}")
  }
}
