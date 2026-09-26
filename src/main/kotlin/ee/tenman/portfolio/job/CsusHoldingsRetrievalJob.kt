package ee.tenman.portfolio.job

import ee.tenman.portfolio.blackrock.CsusHoldingsService
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.model.CollectionSchedules
import ee.tenman.portfolio.service.etf.EtfBreakdownService
import ee.tenman.portfolio.service.etf.EtfHoldingService
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
import ee.tenman.portfolio.service.monitoring.CollectionMonitorService
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
  private val collectionMonitor: CollectionMonitorService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = CollectionSchedules.BLACKROCK_HOLDINGS_STARTUP_SECONDS * 1000, fixedDelay = Long.MAX_VALUE)
  @Scheduled(cron = CollectionSchedules.BLACKROCK_HOLDINGS_CRON, zone = CollectionSchedules.TIME_ZONE)
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
    return collectionMonitor.collect(CollectionKey.BLACKROCK_HOLDINGS, listOf(AVIVA_SYMBOL)) { run ->
      run.attempted(AVIVA_SYMBOL)
      val holdings =
        runCatching { csusHoldingsService.fetchHoldings() }
        .onFailure { run.failed(AVIVA_SYMBOL, it) }
        .getOrThrow()
      if (holdings.isEmpty()) {
        val message = "No CSUS holdings fetched for $today"
        run.failed(AVIVA_SYMBOL, IllegalArgumentException(message))
        log.warn(message)
        return@collect message
      }
      run.fetched(AVIVA_SYMBOL)
      runCatching { etfHoldingService.saveHoldings(AVIVA_SYMBOL, today, holdings) }
        .onFailure { run.failed(AVIVA_SYMBOL, it) }
        .getOrThrow()
      run.persisted(AVIVA_SYMBOL)
      val message = "Saved ${holdings.size} CSUS holdings for Aviva pension on $today"
      log.info(message)
      message
    }
  }

  companion object {
    const val AVIVA_SYMBOL = "GB00B0ZDNB53:GBP"
  }
}
