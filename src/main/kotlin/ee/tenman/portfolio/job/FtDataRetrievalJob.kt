package ee.tenman.portfolio.job

import ee.tenman.portfolio.common.hasPositiveCloses
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.ft.HistoricalPricesService
import ee.tenman.portfolio.model.CollectionRun
import ee.tenman.portfolio.model.CollectionSchedules
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.service.monitoring.CollectionMonitorService
import ee.tenman.portfolio.service.pricing.PriceSnapshotService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Duration
import java.time.Instant

@ScheduledJob
class FtDataRetrievalJob(
  private val instrumentService: InstrumentService,
  private val historicalPricesService: HistoricalPricesService,
  private val dataProcessingUtil: DataProcessingUtil,
  private val jobExecutionService: JobExecutionService,
  private val priceSnapshotService: PriceSnapshotService,
  private val taskScheduler: TaskScheduler,
  private val clock: Clock = Clock.systemDefaultZone(),
  private val collectionMonitor: CollectionMonitorService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Volatile
  private var isExecuting = false

  @PostConstruct
  fun scheduleInitialRun() {
    log.info("Scheduling initial FT data retrieval job to run in 10 seconds")
    taskScheduler.schedule(
      { runScheduledJob() },
      Instant.now(clock).plus(Duration.ofSeconds(CollectionSchedules.FT_HISTORY_STARTUP_SECONDS)),
    )
  }

  @Scheduled(cron = CollectionSchedules.FT_HISTORY_CRON, zone = CollectionSchedules.TIME_ZONE)
  fun runDailyJob() {
    log.info("Running daily FT data retrieval job at 05:00")
    runScheduledJob()
  }

  private fun runScheduledJob() {
    jobExecutionService.executeJob(this)
    log.info("Completed FT data retrieval job")
  }

  override fun execute() {
    if (isExecuting) {
      log.warn("FT data retrieval job is already running, skipping this execution")
      return
    }

    isExecuting = true

    try {
      log.info("Starting FT data retrieval execution")
      val instruments = instrumentService.getInstrumentsByProvider(ProviderName.FT)
      collectionMonitor.collect(CollectionKey.FT_HISTORY, instruments.map { it.symbol }) { run ->
        instruments.forEach { instrument -> processInstrument(instrument, run) }
      }
      log.info("Completed FT data retrieval execution. Processed ${instruments.size} instruments")
    } finally {
      isExecuting = false
    }
  }

  private fun processInstrument(
    instrument: Instrument,
    run: CollectionRun,
  ) {
    run.attempted(instrument.symbol)
    runCatching { retrieveAndProcess(instrument, run) }
      .onFailure { e ->
        run.failed(instrument.symbol, e)
        log.error("Error processing instrument ${instrument.symbol}", e)
      }
  }

  private fun retrieveAndProcess(
    instrument: Instrument,
    run: CollectionRun,
  ) {
    log.info("Retrieving FT data for instrument: ${instrument.symbol}")
    val ftData = historicalPricesService.fetchPrices(instrument.symbol)
    if (!ftData.hasPositiveCloses()) {
      log.warn("No FT data found for instrument: ${instrument.symbol}")
      run.failed(instrument.symbol, IllegalArgumentException("Invalid FT price data for ${instrument.symbol}"))
      return
    }
    run.fetched(instrument.symbol)
    dataProcessingUtil.processDailyData(instrument, ftData, ProviderName.FT)
    val latestPrice = ftData.maxByOrNull { it.key }?.value?.close ?: return
    priceSnapshotService.saveSnapshot(instrument, latestPrice, ProviderName.FT)
    run.persisted(instrument.symbol)
  }
}
