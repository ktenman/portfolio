package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.ft.HistoricalPricesService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.service.pricing.DailyPriceService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@ScheduledJob
class InstrumentPriceGapFillingJob(
  private val instrumentService: InstrumentService,
  private val dailyPriceService: DailyPriceService,
  private val ftHistoricalPricesService: HistoricalPricesService,
  private val jobExecutionService: JobExecutionService,
  private val taskScheduler: TaskScheduler,
  private val clock: Clock = Clock.systemDefaultZone(),
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)
  private val isExecuting = AtomicBoolean(false)

  @PostConstruct
  fun scheduleInitialRun() {
    log.info("Scheduling initial instrument price gap filling job to run in 240 seconds")
    taskScheduler.schedule(
      { runScheduledJob() },
      Instant.now(clock).plus(Duration.ofSeconds(INITIAL_DELAY_SECONDS)),
    )
  }

  @Scheduled(cron = "0 10 6 * * *", zone = "Europe/Tallinn")
  fun runDailyJob() {
    log.info("Running daily instrument price gap filling job at 06:10")
    runScheduledJob()
  }

  private fun runScheduledJob() {
    jobExecutionService.executeJob(this)
    log.info("Completed instrument price gap filling job")
  }

  override fun execute() {
    if (!isExecuting.compareAndSet(false, true)) {
      log.warn("Instrument price gap filling job is already running, skipping")
      return
    }
    try {
      log.info("Starting instrument price gap filling execution")
      val instruments =
        instrumentService
          .getAllInstrumentsWithoutFiltering()
          .filter { it.providerName == ProviderName.LIGHTYEAR }
      if (instruments.isEmpty()) {
        log.info("No LIGHTYEAR instruments found to fill gaps")
        return
      }
      log.info("Found {} LIGHTYEAR instruments to process for gap filling", instruments.size)
      var totalSaved = 0
      instruments.forEach { instrument ->
        val saved = fillGapsForInstrument(instrument)
        totalSaved += saved
      }
      log.info("Instrument price gap filling completed: {} prices saved", totalSaved)
    } finally {
      isExecuting.set(false)
    }
  }

  private fun fillGapsForInstrument(instrument: Instrument): Int {
    try {
      log.debug("Filling price gaps for instrument: {}", instrument.symbol)
      val existingDates = dailyPriceService.findAllExistingDates(instrument)
      val ftData = ftHistoricalPricesService.fetchPrices(instrument.symbol)
      if (ftData.isEmpty()) {
        log.warn("No FT data found for instrument: {}", instrument.symbol)
        return 0
      }
      var savedCount = 0
      ftData.forEach { (date, data) ->
        if (existingDates.contains(date)) return@forEach
        val dailyPrice =
          DailyPrice(
          instrument = instrument,
          entryDate = date,
          providerName = ProviderName.FT,
          openPrice = data.open,
          highPrice = data.high,
          lowPrice = data.low,
          closePrice = data.close,
          volume = data.volume,
        )
        if (dailyPriceService.saveDailyPriceIfNotExists(dailyPrice)) {
          savedCount++
        }
      }
      if (savedCount > 0) {
        log.debug("Saved {} new FT prices for instrument {}", savedCount, instrument.symbol)
      }
      return savedCount
    } catch (e: Exception) {
      log.error("Error filling gaps for instrument {}: {}", instrument.symbol, e.message)
      return 0
    }
  }

  companion object {
    private const val INITIAL_DELAY_SECONDS = 240L
  }
}
