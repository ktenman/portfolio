package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.lightyear.LightyearHistoricalPricesService
import ee.tenman.portfolio.lightyear.LightyearPriceService.Companion.LIGHTYEAR_INSTRUMENTS
import ee.tenman.portfolio.service.InstrumentService
import ee.tenman.portfolio.service.JobExecutionService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
@ConditionalOnProperty(name = ["scheduling.enabled"], havingValue = "true", matchIfMissing = true)
class LightyearHistoricalDataRetrievalJob(
  private val instrumentService: InstrumentService,
  private val lightyearHistoricalPricesService: LightyearHistoricalPricesService,
  private val dataProcessingUtil: DataProcessingUtil,
  private val jobExecutionService: JobExecutionService,
  private val taskScheduler: TaskScheduler,
  private val clock: Clock = Clock.systemDefaultZone(),
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Volatile
  private var isExecuting = false

  @PostConstruct
  fun scheduleInitialRun() {
    log.info("Scheduling initial Lightyear historical data retrieval job to run in 30 seconds")
    taskScheduler.schedule(
      { runScheduledJob() },
      Instant.now(clock).plus(Duration.ofSeconds(30)),
    )
  }

  @Scheduled(cron = "0 30 5 * * *")
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
      val instruments =
        instrumentService
          .getAllInstrumentsWithoutFiltering()
          .filter { it.providerName == ProviderName.LIGHTYEAR }

      if (instruments.isEmpty()) {
        log.info("No Lightyear instruments found to process")
        return
      }

      instruments.forEach { instrument ->
        try {
          processInstrument(instrument)
        } catch (e: Exception) {
          log.error("Error processing instrument ${instrument.symbol}", e)
        }
      }

      log.info("Completed Lightyear historical data retrieval execution. Processed ${instruments.size} instruments")
    } finally {
      isExecuting = false
    }
  }

  private fun processInstrument(instrument: ee.tenman.portfolio.domain.Instrument) {
    val uuid =
      requireNotNull(LIGHTYEAR_INSTRUMENTS[instrument.symbol]) {
        "No UUID mapping found for LIGHTYEAR instrument: ${instrument.symbol}. Add it to LIGHTYEAR_INSTRUMENTS map."
      }

    log.info("Retrieving Lightyear historical data for instrument: ${instrument.symbol}")
    val historicalData = lightyearHistoricalPricesService.fetchHistoricalPrices(uuid)

    if (historicalData.isEmpty()) {
      log.warn("No historical data found for instrument: ${instrument.symbol}")
      return
    }

    dataProcessingUtil.processDailyData(instrument, historicalData, ProviderName.LIGHTYEAR)
  }
}
