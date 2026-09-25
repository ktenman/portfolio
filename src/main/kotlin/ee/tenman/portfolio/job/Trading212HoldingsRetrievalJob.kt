package ee.tenman.portfolio.job

import ee.tenman.portfolio.configuration.Trading212ScrapingProperties
import ee.tenman.portfolio.configuration.Trading212SymbolEntry
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.model.CollectionRun
import ee.tenman.portfolio.model.CollectionSchedules
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.etf.EtfBreakdownService
import ee.tenman.portfolio.service.etf.EtfHoldingService
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
import ee.tenman.portfolio.service.monitoring.CollectionMonitorService
import ee.tenman.portfolio.trading212.Trading212HoldingsService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@ScheduledJob
class Trading212HoldingsRetrievalJob(
  private val jobTransactionService: JobTransactionService,
  private val scrapingProperties: Trading212ScrapingProperties,
  private val holdingsService: Trading212HoldingsService,
  private val etfHoldingService: EtfHoldingService,
  private val etfBreakdownService: EtfBreakdownService,
  private val instrumentRepository: InstrumentRepository,
  private val clock: Clock,
  private val collectionMonitor: CollectionMonitorService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = CollectionSchedules.HOLDINGS_STARTUP_SECONDS * 1000, fixedDelay = Long.MAX_VALUE)
  @Scheduled(cron = CollectionSchedules.TRADING212_HOLDINGS_CRON, zone = CollectionSchedules.TIME_ZONE)
  fun runJob() {
    log.info("Running Trading212 holdings retrieval job for ${scrapingProperties.symbols.size} configured symbols")
    val startTime = Instant.now(clock)
    var status = JobStatus.SUCCESS
    var message: String? = null
    try {
      message = fetchAllSymbols()
      etfBreakdownService.evictBreakdownCache()
      log.info("Completed Trading212 holdings retrieval job successfully")
    } catch (e: Exception) {
      status = JobStatus.FAILURE
      message = "Trading212 holdings job failed: ${e.message}"
      log.error("Trading212 holdings job failed", e)
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
    fetchAllSymbols()
  }

  private fun fetchAllSymbols(): String {
    val today = LocalDate.now(clock)
    val trading212Symbols =
      instrumentRepository
        .findByProviderName(ProviderName.TRADING212)
        .map { it.symbol }
        .toSet()
    val eligibleEntries = scrapingProperties.symbols.filter { it.symbol in trading212Symbols }
    log.info("Processing ${eligibleEntries.size} Trading212-provider symbols out of ${scrapingProperties.symbols.size} configured")
    val results = mutableListOf<String>()
    collectionMonitor.collect(CollectionKey.TRADING212_HOLDINGS, eligibleEntries.map { it.symbol }) { run ->
      eligibleEntries.forEach { entry ->
        run.attempted(entry.symbol)
        runCatching { processSymbol(entry, today, run) }
          .onSuccess { results.add(it) }
          .onFailure { e ->
            run.failed(entry.symbol, e)
            val msg = "Failed to process ${entry.symbol}: ${e.message}"
            log.error(msg, e)
            results.add(msg)
          }
      }
    }
    return results.joinToString("\n")
  }

  private fun processSymbol(
    entry: Trading212SymbolEntry,
    today: LocalDate,
    run: CollectionRun,
  ): String {
    log.info("Fetching holdings for Trading212 ETF: ${entry.symbol} (ticker=${entry.ticker})")
    val holdings = holdingsService.fetchHoldings(entry.ticker)
    if (holdings.isEmpty()) {
      val msg = "No holdings found for ${entry.symbol}"
      log.warn(msg)
      run.failed(entry.symbol, IllegalArgumentException(msg))
      return msg
    }
    run.fetched(entry.symbol)
    etfHoldingService.saveHoldings(
      etfSymbol = entry.symbol,
      date = today,
      holdings = holdings,
    )
    run.persisted(entry.symbol)
    val msg = "Successfully fetched and saved ${holdings.size} holdings for ${entry.symbol}"
    log.info(msg)
    return msg
  }
}
