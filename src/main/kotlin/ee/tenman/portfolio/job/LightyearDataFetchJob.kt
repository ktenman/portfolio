package ee.tenman.portfolio.job

import ee.tenman.portfolio.configuration.LightyearScrapingProperties
import ee.tenman.portfolio.configuration.LightyearScrapingProperties.EtfConfig
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.service.EtfBreakdownService
import ee.tenman.portfolio.service.EtfHoldingsService
import ee.tenman.portfolio.service.JobTransactionService
import ee.tenman.portfolio.service.LightyearScraperService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

@Component
@ConditionalOnProperty(name = ["scheduling.enabled"], havingValue = "true", matchIfMissing = true)
class LightyearDataFetchJob(
  private val jobTransactionService: JobTransactionService,
  private val lightyearScrapingProperties: LightyearScrapingProperties,
  private val lightyearScraperService: LightyearScraperService,
  private val etfHoldingsService: EtfHoldingsService,
  private val etfBreakdownService: EtfBreakdownService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = 15000, fixedDelay = Long.MAX_VALUE)
  @Scheduled(cron = "0 50 23 * * ?")
  fun runJob() {
    log.info("Running Lightyear data fetch job for ${lightyearScrapingProperties.etfs.size} ETFs")
    val start = Instant.now()
    val result =
      runCatching {
      val message = fetchAll()
      etfBreakdownService.evict()
      log.info("Completed Lightyear data fetch job successfully")
      message
    }

    jobTransactionService.saveJobExecution(
      job = this,
      startTime = start,
      endTime = Instant.now(),
      status = if (result.isSuccess) JobStatus.SUCCESS else JobStatus.FAILURE,
      message = result.getOrElse { "Failed to fetch data: ${it.message}" },
    )
    result.onFailure { log.error("Lightyear data fetch job failed", it) }
  }

  override fun execute() {
    fetchAll()
  }

  private fun fetchAll(): String {
    val today = LocalDate.now()
    return lightyearScrapingProperties.etfs.map { fetch(it, today) }.joinToString("\n")
  }

  private fun fetch(
    config: EtfConfig,
    date: LocalDate,
  ): String =
    runCatching {
    if (config.skipHoldings) {
      val msg = "Holdings scraping disabled for ${config.symbol} (using WisdomTree job instead)"
      log.info(msg)
      return msg
    }

    if (etfHoldingsService.hasHoldingsForDate(config.symbol, date)) {
      val msg = "Holdings for ${config.symbol} already exist for $date, skipping"
      log.info(msg)
      return msg
    }

    log.info("Fetching holdings for ETF: ${config.symbol}")
    val holdings = lightyearScraperService.fetchEtfHoldings(config)
    if (holdings.isEmpty()) {
      val msg = "No holdings found for ${config.symbol}"
      log.warn(msg)
      return msg
    }

    etfHoldingsService.saveHoldings(config.symbol, date, holdings)
    val msg = "Successfully fetched and saved ${holdings.size} holdings for ${config.symbol}"
    log.info(msg)
    msg
  }.getOrElse { e ->
    val msg = "Failed to process ${config.symbol}: ${e.message}"
    log.error(msg, e)
    msg
  }
}
