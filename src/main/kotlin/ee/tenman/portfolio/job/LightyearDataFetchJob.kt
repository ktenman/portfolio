package ee.tenman.portfolio.job

import ee.tenman.portfolio.configuration.LightyearScrapingProperties
import ee.tenman.portfolio.domain.JobStatus
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
  private val properties: LightyearScrapingProperties,
  private val scraperService: LightyearScraperService,
  private val etfHoldingsService: EtfHoldingsService,
  private val etfBreakdownService: ee.tenman.portfolio.service.EtfBreakdownService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = 15000, fixedDelay = Long.MAX_VALUE)
  @Scheduled(cron = "0 50 23 * * ?")
  fun runJob() {
    log.info("Running Lightyear data fetch job for ${properties.etfs.size} ETFs")
    val startTime = Instant.now()
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
      val endTime = Instant.now()
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
    val today = LocalDate.now()
    val results = mutableListOf<String>()

    properties.etfs.forEach { etfConfig ->
      try {
        if (etfConfig.skipHoldings) {
          val msg = "Holdings scraping disabled for ${etfConfig.symbol} (using WisdomTree job instead)"
          log.info(msg)
          results.add(msg)
          return@forEach
        }

        if (etfHoldingsService.hasHoldingsForDate(etfConfig.symbol, today)) {
          val msg = "Holdings for ${etfConfig.symbol} already exist for $today, skipping"
          log.info(msg)
          results.add(msg)
          return@forEach
        }

        log.info("Fetching holdings for ETF: ${etfConfig.symbol}")
        val holdings = scraperService.fetchEtfHoldings(etfConfig)

        if (holdings.isNotEmpty()) {
          etfHoldingsService.saveHoldings(
            etfSymbol = etfConfig.symbol,
            date = today,
            holdings = holdings,
          )
          val msg = "Successfully fetched and saved ${holdings.size} holdings for ${etfConfig.symbol}"
          log.info(msg)
          results.add(msg)
        } else {
          val msg = "No holdings found for ${etfConfig.symbol}"
          log.warn(msg)
          results.add(msg)
        }
      } catch (e: Exception) {
        val msg = "Failed to process ${etfConfig.symbol}: ${e.message}"
        log.error(msg, e)
        results.add(msg)
      }
    }

    return results.joinToString("\n")
  }
}
