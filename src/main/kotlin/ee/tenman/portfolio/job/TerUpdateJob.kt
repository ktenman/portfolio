package ee.tenman.portfolio.job

import ee.tenman.portfolio.configuration.Trading212ScrapingProperties
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.lightyear.LightyearFundInfoData
import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
import ee.tenman.portfolio.service.instrument.FundCurrencyResolverService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.trading212.Trading212HoldingsService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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
  private val fundCurrencyResolver: FundCurrencyResolverService,
  private val clock: Clock,
  @Value("\${ter.startup.max-attempts:5}")
  private val startupMaxAttempts: Int = 5,
  @Value("\${ter.startup.retry-delay-millis:10000}")
  private val startupRetryDelayMillis: Long = 10000,
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
      retryUntilUpdated()
    }
  }

  internal fun retryUntilUpdated() {
    repeat(startupMaxAttempts) { attempt ->
      if (executeWithTracking() > 0) return
      if (attempt < startupMaxAttempts - 1) {
        log.warn("TER startup attempt ${attempt + 1} updated nothing, retrying in ${startupRetryDelayMillis}ms")
        Thread.sleep(startupRetryDelayMillis)
      }
    }
  }

  @Scheduled(cron = "0 0 3 * * SUN")
  fun runJob() {
    executeWithTracking()
  }

  private fun executeWithTracking(): Int {
    log.info("Running TER update job")
    val startTime = Instant.now(clock)
    var status = JobStatus.SUCCESS
    var message: String? = null
    var updated = 0
    try {
      val (count, summary) = updateAll()
      updated = count
      message = summary
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
    return updated
  }

  override fun execute() {
    updateAll()
  }

  private fun updateAll(): Pair<Int, String> {
    val (lyCount, lyResult) =
      processProvider(ProviderName.LIGHTYEAR, "Lightyear") { instrument ->
        val lightyearData = fetchLightyearData(instrument)
        (TER_OVERRIDES[instrument.symbol] ?: lightyearData?.ter) to lightyearData
      }
    val (t212Count, t212Result) =
      processProvider(ProviderName.TRADING212, "Trading212") { instrument ->
        resolveTrading212Ter(instrument) to null
      }
    return (lyCount + t212Count) to "$lyResult; $t212Result"
  }

  private fun processProvider(
    provider: ProviderName,
    label: String,
    fetcher: (Instrument) -> Pair<BigDecimal?, LightyearFundInfoData?>,
  ): Pair<Int, String> {
    val instruments = instrumentRepository.findByProviderName(provider)
    log.info("Found ${instruments.size} $label instruments to update TERs")
    var updated = 0
    var noData = 0
    instruments.forEach { instrument ->
      val (ter, lightyearData) = fetcher(instrument)
      if (ter != null) {
        instrumentService.updateTer(instrument.id, ter)
        updated++
        log.debug("Updated TER for ${instrument.symbol}: $ter")
      } else {
        noData++
      }
      persistFundCurrency(instrument, lightyearData)
    }
    return updated to "$label: updated $updated/${instruments.size}, $noData with no data"
  }

  private fun fetchLightyearData(instrument: Instrument): LightyearFundInfoData? =
    runCatching { lightyearPriceService.fetchFundInfo(instrument.symbol) }.getOrElse {
      log.error("Failed to fetch Lightyear fund info for ${instrument.symbol}", it)
      null
    }

  private fun persistFundCurrency(
    instrument: Instrument,
    lightyearData: LightyearFundInfoData?,
  ) {
    val resolved = fundCurrencyResolver.resolve(instrument, lightyearData)
    if (resolved != null && resolved != instrument.fundCurrency) {
      instrumentService.updateFundCurrency(instrument.id, resolved)
      log.info("Updated fundCurrency for ${instrument.symbol}: $resolved")
    }
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
}
