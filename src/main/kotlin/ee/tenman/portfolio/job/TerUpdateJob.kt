package ee.tenman.portfolio.job

import ee.tenman.portfolio.configuration.Trading212ScrapingProperties
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.trading212.Trading212HoldingsService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CompletableFuture

@ScheduledJob
class TerUpdateJob(
  private val jobTransactionService: JobTransactionService,
  private val instrumentRepository: InstrumentRepository,
  private val lightyearPriceService: LightyearPriceService,
  private val instrumentService: InstrumentService,
  private val trading212HoldingsService: Trading212HoldingsService,
  private val scrapingProperties: Trading212ScrapingProperties,
  private val clock: Clock,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private val TER_OVERRIDES =
      mapOf(
        "EXUS:GER:EUR" to BigDecimal("0.15"),
      )
  }

  @PostConstruct
  fun onStartup() {
    CompletableFuture.runAsync {
      log.info("Running TER update job on startup")
      runJob()
    }
  }

  @Scheduled(cron = "0 0 3 * * SUN")
  fun runJob() {
    log.info("Running TER update job")
    val startTime = Instant.now(clock)
    var status = JobStatus.SUCCESS
    var message: String? = null
    try {
      message = updateAllTers()
      log.info("Completed TER update job successfully")
    } catch (e: Exception) {
      status = JobStatus.FAILURE
      message = "Failed to update TERs: ${e.message}"
      log.error("TER update job failed", e)
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
    updateAllTers()
  }

  private fun updateAllTers(): String {
    val lyResult =
      processProvider(
        instruments = instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR),
        providerLabel = "Lightyear",
      ) { instrument ->
        TER_OVERRIDES[instrument.symbol] ?: lightyearPriceService.fetchFundInfo(instrument.symbol)
      }
    val t212Result =
      processProvider(
        instruments = instrumentRepository.findByProviderName(ProviderName.TRADING212),
        providerLabel = "Trading212",
      ) { instrument ->
        resolveTrading212Ter(instrument)
      }
    return "$lyResult; $t212Result"
  }

  private fun resolveTrading212Ter(instrument: Instrument): BigDecimal? {
    val ticker = scrapingProperties.findTickerBySymbol(instrument.symbol)
    if (ticker == null) {
      log.warn("No Trading212 ticker mapping for symbol ${instrument.symbol}, skipping TER update")
      return null
    }
    return runCatching { trading212HoldingsService.fetchTer(ticker) }.getOrElse {
      log.error("Failed to fetch TER for ${instrument.symbol} via Trading212", it)
      null
    }
  }

  private fun processProvider(
    instruments: List<Instrument>,
    providerLabel: String,
    terFetcher: (Instrument) -> BigDecimal?,
  ): String {
    log.info("Found ${instruments.size} $providerLabel instruments to update TERs")
    var updated = 0
    var noData = 0
    instruments.forEach { instrument ->
      val ter = terFetcher(instrument)
      if (ter != null) {
        instrumentService.updateTer(instrument.id, ter)
        updated++
        log.debug("Updated TER for ${instrument.symbol}: $ter")
      } else {
        noData++
        log.debug("No TER data for ${instrument.symbol}")
      }
    }
    val resultMessage = "$providerLabel: updated $updated/${instruments.size}, $noData with no data"
    log.info(resultMessage)
    return resultMessage
  }
}
