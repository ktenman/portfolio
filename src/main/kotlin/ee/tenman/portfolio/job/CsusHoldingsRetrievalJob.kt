package ee.tenman.portfolio.job

import ee.tenman.portfolio.blackrock.CsusHoldingsService
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.service.etf.EtfBreakdownService
import ee.tenman.portfolio.service.etf.EtfHoldingService
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@ScheduledJob
class CsusHoldingsRetrievalJob(
  private val jobTransactionService: JobTransactionService,
  private val csusHoldingsService: CsusHoldingsService,
  private val etfHoldingService: EtfHoldingService,
  private val etfBreakdownService: EtfBreakdownService,
  private val clock: Clock,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = 20000, fixedDelay = Long.MAX_VALUE)
  @Scheduled(cron = "0 50 23 * * ?")
  fun runJob() {
    val startTime = Instant.now(clock)
    var status = JobStatus.SUCCESS
    var message: String? = null
    try {
      message = retrieveHoldings()
      etfBreakdownService.evictBreakdownCache()
      log.info("Completed CSUS holdings retrieval job successfully")
    } catch (e: Exception) {
      status = JobStatus.FAILURE
      message = "CSUS holdings job failed: ${e.message}"
      log.error("CSUS holdings job failed", e)
    } finally {
      jobTransactionService.saveJobExecution(
        job = this,
        startTime = startTime,
        endTime = Instant.now(clock),
        status = status,
        message = message,
      )
    }
  }

  override fun execute() {
    retrieveHoldings()
  }

  private fun retrieveHoldings(): String {
    val today = LocalDate.now(clock)
    if (etfHoldingService.hasHoldingsForDate(AVIVA_SYMBOL, today)) {
      val message = "CSUS holdings already exist for $today, skipping"
      log.info(message)
      return message
    }
    val holdings = csusHoldingsService.fetchHoldings()
    if (holdings.isEmpty()) {
      val message = "No CSUS holdings fetched for $today"
      log.warn(message)
      return message
    }
    etfHoldingService.saveHoldings(
      etfSymbol = AVIVA_SYMBOL,
      date = today,
      holdings = holdings,
    )
    val message = "Saved ${holdings.size} CSUS holdings for Aviva pension on $today"
    log.info(message)
    return message
  }

  companion object {
    private const val AVIVA_SYMBOL = "GB00B0ZDNB53:GBP"
  }
}
