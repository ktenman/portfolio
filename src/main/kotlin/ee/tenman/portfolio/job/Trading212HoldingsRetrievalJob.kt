package ee.tenman.portfolio.job

import ee.tenman.portfolio.configuration.Trading212ScrapingProperties
import ee.tenman.portfolio.configuration.Trading212SymbolEntry
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.etf.EtfBreakdownService
import ee.tenman.portfolio.service.etf.EtfHoldingService
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
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
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = 15000, fixedDelay = Long.MAX_VALUE)
  @Scheduled(cron = "0 40 23 * * ?")
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
    eligibleEntries.forEach { entry ->
      try {
        val msg = processSymbol(entry, today)
        results.add(msg)
      } catch (e: Exception) {
        val msg = "Failed to process ${entry.symbol}: ${e.message}"
        log.error(msg, e)
        results.add(msg)
      }
    }
    return results.joinToString("\n")
  }

  private fun processSymbol(
    entry: Trading212SymbolEntry,
    today: LocalDate,
  ): String {
    if (etfHoldingService.hasHoldingsForDate(entry.symbol, today)) {
      val msg = "Holdings for ${entry.symbol} already exist for $today, skipping"
      log.info(msg)
      return msg
    }
    log.info("Fetching holdings for Trading212 ETF: ${entry.symbol} (ticker=${entry.ticker})")
    val holdings = holdingsService.fetchHoldings(entry.ticker)
    if (holdings.isEmpty()) {
      val msg = "No holdings found for ${entry.symbol}"
      log.warn(msg)
      return msg
    }
    etfHoldingService.saveHoldings(
      etfSymbol = entry.symbol,
      date = today,
      holdings = holdings,
    )
    val msg = "Successfully fetched and saved ${holdings.size} holdings for ${entry.symbol}"
    log.info(msg)
    return msg
  }
}
