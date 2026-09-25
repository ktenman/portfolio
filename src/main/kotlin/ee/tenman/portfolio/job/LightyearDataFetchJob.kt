package ee.tenman.portfolio.job

import ee.tenman.portfolio.configuration.LightyearScrapingProperties
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.model.CollectionRun
import ee.tenman.portfolio.model.CollectionSchedules
import ee.tenman.portfolio.service.etf.EtfHoldingService
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
import ee.tenman.portfolio.service.monitoring.CollectionMonitorService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@ScheduledJob
class LightyearDataFetchJob(
  private val jobTransactionService: JobTransactionService,
  private val properties: LightyearScrapingProperties,
  private val lightyearPriceService: LightyearPriceService,
  private val etfHoldingService: EtfHoldingService,
  private val etfBreakdownService: ee.tenman.portfolio.service.etf.EtfBreakdownService,
  private val clock: Clock,
  private val collectionMonitor: CollectionMonitorService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = CollectionSchedules.HOLDINGS_STARTUP_SECONDS * 1000, fixedDelay = Long.MAX_VALUE)
  @Scheduled(cron = CollectionSchedules.LIGHTYEAR_HOLDINGS_CRON, zone = CollectionSchedules.TIME_ZONE)
  fun runJob() {
    log.info("Running Lightyear data fetch job for ${properties.etfs.size} ETFs")
    val startTime = Instant.now(clock)
    var status = JobStatus.SUCCESS
    var message: String? = null

    try {
      message = fetchAllEtfs()
      etfBreakdownService.evictBreakdownCache()
      log.info("Completed Lightyear data fetch job successfully")
    } catch (e: Exception) {
      status = JobStatus.FAILURE
      message = "Failed to fetch data: ${e.message}"
      log.error("Lightyear data fetch job failed", e)
    } finally {
      val endTime = Instant.now(clock)
      jobTransactionService.saveJobExecution(
        job = this,
        startTime = startTime,
        endTime = endTime,
        status = status,
        message = message,
      )
    }
  }

  override fun execute() {
    fetchAllEtfs()
  }

  private fun fetchAllEtfs(): String {
    val today = LocalDate.now(clock)
    val results = mutableListOf<String>()
    val symbols = properties.getHoldingsSymbols()
    collectionMonitor.collect(CollectionKey.LIGHTYEAR_HOLDINGS, symbols) { run ->
      symbols.forEach { symbol ->
        run.attempted(symbol)
        runCatching { processEtf(symbol, today, run) }
          .onSuccess { results.add(it) }
          .onFailure { e ->
            run.failed(symbol, e)
            val msg = "Failed to process $symbol: ${e.message}"
            log.error(msg, e)
            results.add(msg)
          }
      }
    }
    return results.joinToString("\n")
  }

  private fun processEtf(
    symbol: String,
    today: LocalDate,
    run: CollectionRun,
  ): String {
    log.info("Fetching holdings for ETF: $symbol")
    val holdings = lightyearPriceService.fetchHoldingsAsDto(symbol)
    if (holdings.isEmpty()) {
      val msg = "No holdings found for $symbol"
      log.warn(msg)
      run.failed(symbol, IllegalArgumentException(msg))
      return msg
    }
    run.fetched(symbol)
    etfHoldingService.saveHoldings(
      etfSymbol = symbol,
      date = today,
      holdings = holdings,
    )
    run.persisted(symbol)
    val msg = "Successfully fetched and saved ${holdings.size} holdings for $symbol"
    log.info(msg)
    return msg
  }
}
