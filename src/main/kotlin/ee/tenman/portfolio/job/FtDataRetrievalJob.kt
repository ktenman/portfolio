package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.ft.HistoricalPricesService
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
@ConditionalOnProperty(name = ["ft.job.enabled"], havingValue = "true", matchIfMissing = false)
class FtDataRetrievalJob(
  private val instrumentService: InstrumentService,
  private val historicalPricesService: HistoricalPricesService,
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
    log.info("Scheduling initial FT data retrieval job to run in 10 seconds")
    taskScheduler.schedule(
      { runScheduledJob() },
      Instant.now(clock).plus(Duration.ofSeconds(10)),
    )
  }

  @Scheduled(cron = "0 0 5 * * *")
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
      val instruments =
        instrumentService
          .getAllInstruments()
          .filter { it.providerName == ProviderName.FT }

      if (instruments.isEmpty()) {
        log.info("No FT instruments found to process")
        return
      }

      instruments.forEach { instrument ->
        processInstrument(instrument)
      }

      log.info("Completed FT data retrieval execution. Processed ${instruments.size} instruments")
    } finally {
      isExecuting = false
    }
  }

  private fun processInstrument(instrument: ee.tenman.portfolio.domain.Instrument) {
    try {
      log.info("Retrieving FT data for instrument: ${instrument.symbol}")
      val ftData = historicalPricesService.fetchPrices(instrument.symbol)

      if (ftData.isEmpty()) {
        log.warn("No FT data found for instrument: ${instrument.symbol}")
        return
      }

      dataProcessingUtil.processDailyData(instrument, ftData, ProviderName.FT)
    } catch (e: Exception) {
      log.error("Error processing instrument ${instrument.symbol}", e)
    }
  }
}
