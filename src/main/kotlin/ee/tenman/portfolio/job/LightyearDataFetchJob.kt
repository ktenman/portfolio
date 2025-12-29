package ee.tenman.portfolio.job

import ee.tenman.portfolio.configuration.LightyearScrapingProperties
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.service.etf.EtfHoldingsService
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
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
  private val etfHoldingsService: EtfHoldingsService,
  private val etfBreakdownService: ee.tenman.portfolio.service.etf.EtfBreakdownService,
  private val clock: Clock,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = 15000, fixedDelay = Long.MAX_VALUE)
  @Scheduled(cron = "0 50 23 * * ?")
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

    properties.etfs.forEach { etfConfig ->
      try {
        val msg = processEtf(etfConfig, today)
        results.add(msg)
      } catch (e: Exception) {
        val msg = "Failed to process ${etfConfig.symbol}: ${e.message}"
        log.error(msg, e)
        results.add(msg)
      }
    }

    return results.joinToString("\n")
  }

  private fun processEtf(
    etfConfig: LightyearScrapingProperties.EtfConfig,
    today: LocalDate,
  ): String {
    if (etfHoldingsService.hasHoldingsForDate(etfConfig.symbol, today)) {
      val msg = "Holdings for ${etfConfig.symbol} already exist for $today, skipping"
      log.info(msg)
      return msg
    }

    log.info("Fetching holdings for ETF: ${etfConfig.symbol}")
    val holdings = lightyearPriceService.fetchHoldingsAsDto(etfConfig.symbol)

    if (holdings.isEmpty()) {
      val msg = "No holdings found for ${etfConfig.symbol}"
      log.warn(msg)
      return msg
    }

    etfHoldingsService.saveHoldings(
      etfSymbol = etfConfig.symbol,
      date = today,
      holdings = holdings,
    )

    val msg = "Successfully fetched and saved ${holdings.size} holdings for ${etfConfig.symbol}"
    log.info(msg)
    return msg
  }
}
