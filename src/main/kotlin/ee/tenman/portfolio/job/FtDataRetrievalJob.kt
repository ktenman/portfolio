package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Instrument
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
  private var executing = false

  @PostConstruct
  fun scheduleInitialRun() {
    log.info("Scheduling initial FT data retrieval job to run in 10 seconds")
    taskScheduler.schedule({ run() }, Instant.now(clock).plus(Duration.ofSeconds(10)))
  }

  @Scheduled(cron = "0 0 5 * * *")
  fun runDailyJob() {
    log.info("Running daily FT data retrieval job at 05:00")
    run()
  }

  private fun run() {
    jobExecutionService.executeJob(this)
    log.info("Completed FT data retrieval job")
  }

  override fun execute() {
    if (executing) {
      log.warn("FT data retrieval job is already running, skipping this execution")
      return
    }

    executing = true
    try {
      process()
    } finally {
      executing = false
    }
  }

  private fun process() {
    log.info("Starting FT data retrieval execution")
    val instruments = instrumentService.findAll().filter { it.providerName == ProviderName.FT }
    if (instruments.isEmpty()) {
      log.info("No FT instruments found to process")
      return
    }

    instruments.forEach { fetch(it) }
    log.info("Completed FT data retrieval execution. Processed ${instruments.size} instruments")
  }

  private fun fetch(instrument: Instrument) {
    runCatching {
      log.info("Retrieving FT data for instrument: ${instrument.symbol}")
      val data = historicalPricesService.fetchPrices(instrument.symbol)
      if (data.isEmpty()) {
        log.warn("No FT data found for instrument: ${instrument.symbol}")
        return
      }
      dataProcessingUtil.processDailyData(instrument, data, ProviderName.FT)
    }.onFailure { log.error("Error processing instrument ${instrument.symbol}", it) }
  }
}
