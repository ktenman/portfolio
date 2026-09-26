package ee.tenman.portfolio.job

import ee.tenman.portfolio.common.hasPositiveCloses
import ee.tenman.portfolio.configuration.LightyearScrapingProperties
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.lightyear.LightyearHistoricalPricesService
import ee.tenman.portfolio.model.CollectionRun
import ee.tenman.portfolio.model.CollectionSchedules
import ee.tenman.portfolio.service.currency.CurrencyConversionService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.service.monitoring.CollectionMonitorService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Duration
import java.time.Instant

@ScheduledJob
class LightyearHistoricalDataRetrievalJob(
  private val instrumentService: InstrumentService,
  private val lightyearHistoricalPricesService: LightyearHistoricalPricesService,
  private val dataProcessingUtil: DataProcessingUtil,
  private val currencyConversionService: CurrencyConversionService,
  private val jobExecutionService: JobExecutionService,
  private val taskScheduler: TaskScheduler,
  private val lightyearProperties: LightyearScrapingProperties,
  private val clock: Clock = Clock.systemDefaultZone(),
  private val collectionMonitor: CollectionMonitorService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Volatile
  private var isExecuting = false

  @PostConstruct
  fun scheduleInitialRun() {
    log.info("Scheduling initial Lightyear historical data retrieval job to run in 30 seconds")
    taskScheduler.schedule(
      { runScheduledJob() },
      Instant.now(clock).plus(Duration.ofSeconds(CollectionSchedules.LIGHTYEAR_HISTORY_STARTUP_SECONDS)),
    )
  }

  @Scheduled(cron = CollectionSchedules.LIGHTYEAR_HISTORY_CRON, zone = CollectionSchedules.TIME_ZONE)
  fun runDailyJob() {
    log.info("Running daily Lightyear historical data retrieval job at 05:30")
    runScheduledJob()
  }

  private fun runScheduledJob() {
    jobExecutionService.executeJob(this)
    log.info("Completed Lightyear historical data retrieval job")
  }

  override fun execute() {
    if (isExecuting) {
      log.warn("Lightyear historical data retrieval job is already running, skipping this execution")
      return
    }

    isExecuting = true

    try {
      log.info("Starting Lightyear historical data retrieval execution")
      val instruments = instrumentService.getInstrumentsByProvider(ProviderName.LIGHTYEAR)

      collectionMonitor.collect(CollectionKey.LIGHTYEAR_HISTORY, instruments.map { it.symbol }) { run ->
        instruments.forEach { instrument ->
          run.attempted(instrument.symbol)
          runCatching { processInstrument(instrument, run) }.onFailure { e ->
            run.failed(instrument.symbol, e)
            log.error("Error processing instrument ${instrument.symbol}", e)
          }
        }
      }
      log.info("Completed Lightyear historical data retrieval execution. Processed ${instruments.size} instruments")
    } finally {
      isExecuting = false
    }
  }

  private fun processInstrument(
    instrument: Instrument,
    run: CollectionRun,
  ) {
    val uuid = lightyearProperties.findUuidBySymbol(instrument.symbol)
    if (uuid == null) {
      log.warn("No UUID mapping found for LIGHTYEAR instrument: ${instrument.symbol}")
      run.failed(instrument.symbol, IllegalArgumentException("No UUID mapping for ${instrument.symbol}"))
      return
    }
    log.info("Retrieving Lightyear historical data for instrument: ${instrument.symbol}")
    val historicalData = lightyearHistoricalPricesService.fetchHistoricalPrices(uuid)
    if (!historicalData.hasPositiveCloses()) {
      log.warn("No historical data found for instrument: ${instrument.symbol}")
      run.failed(instrument.symbol, IllegalArgumentException("Invalid historical data for ${instrument.symbol}"))
      return
    }
    run.fetched(instrument.symbol)
    val pricesInEur = currencyConversionService.convertDailyPricesToEur(historicalData, listingCurrency(instrument.symbol))
    require(pricesInEur.hasPositiveCloses()) {
      "Invalid converted historical data for ${instrument.symbol}"
    }
    dataProcessingUtil.processDailyData(instrument, pricesInEur, ProviderName.LIGHTYEAR)
    run.persisted(instrument.symbol)
  }

  private fun listingCurrency(symbol: String): Currency = Currency.fromCodeOrNull(symbol.substringAfterLast(':')) ?: Currency.EUR
}
