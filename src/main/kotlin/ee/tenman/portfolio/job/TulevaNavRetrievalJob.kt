package ee.tenman.portfolio.job

import ee.tenman.portfolio.common.hasPositiveCloses
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.model.CollectionRun
import ee.tenman.portfolio.model.CollectionSchedules
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.service.monitoring.CollectionMonitorService
import ee.tenman.portfolio.tuleva.TulevaNavClient
import ee.tenman.portfolio.tuleva.toDailyPrices
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.LocalDate

@ScheduledJob
class TulevaNavRetrievalJob(
  private val instrumentService: InstrumentService,
  private val tulevaNavClient: TulevaNavClient,
  private val dataProcessingUtil: DataProcessingUtil,
  private val jobExecutionService: JobExecutionService,
  private val collectionMonitor: CollectionMonitorService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = CollectionSchedules.TULEVA_HISTORY_STARTUP_SECONDS * 1000, fixedDelay = Long.MAX_VALUE)
  fun runStartupImport() {
    val symbols = instrumentService.getInstrumentsByProvider(ProviderName.TULEVA).map { it.symbol }
    if (collectionMonitor.current(CollectionKey.TULEVA_HISTORY, symbols)) {
      log.info("Skipping startup Tuleva NAV import because the last full collection is still current")
      return
    }
    jobExecutionService.executeJob(this)
  }

  @Scheduled(cron = CollectionSchedules.TULEVA_HISTORY_CRON, zone = CollectionSchedules.TIME_ZONE)
  fun runScheduledJob() {
    jobExecutionService.executeJob(this)
  }

  override fun execute() {
    val instruments = instrumentService.getInstrumentsByProvider(ProviderName.TULEVA)
    collectionMonitor.collect(CollectionKey.TULEVA_HISTORY, instruments.map { it.symbol }) { run ->
      instruments.forEach { instrument -> processInstrument(instrument, run) }
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
        log.error("Error retrieving Tuleva NAV for ${instrument.symbol}", e)
      }
  }

  private fun retrieveAndProcess(
    instrument: Instrument,
    run: CollectionRun,
  ) {
    val prices = tulevaNavClient.getNav(instrument.symbol, HISTORY_START).toDailyPrices()
    require(prices.hasPositiveCloses()) { "Invalid Tuleva NAV data for ${instrument.symbol}" }
    run.fetched(instrument.symbol)
    dataProcessingUtil.processDailyData(instrument, prices, ProviderName.TULEVA)
    run.persisted(instrument.symbol)
  }

  companion object {
    private val HISTORY_START: LocalDate = LocalDate.of(2017, 1, 1)
  }
}
