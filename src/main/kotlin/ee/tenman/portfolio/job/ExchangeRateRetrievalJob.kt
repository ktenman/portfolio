package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.ecb.EcbClient
import ee.tenman.portfolio.ecb.EcbCsvParser
import ee.tenman.portfolio.service.currency.ExchangeRateService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@ScheduledJob
class ExchangeRateRetrievalJob(
  private val ecbClient: EcbClient,
  private val exchangeRateService: ExchangeRateService,
  private val jobExecutionService: JobExecutionService,
  private val taskScheduler: TaskScheduler,
  private val clock: Clock = Clock.systemDefaultZone(),
) : Job {
  companion object {
    private val BACKFILL_START = LocalDate.of(2014, 12, 1)
    private const val CSV_FORMAT = "csvdata"
  }

  private val log = LoggerFactory.getLogger(javaClass)

  @PostConstruct
  fun scheduleInitialRun() {
    log.info("Scheduling initial exchange rate retrieval job to run in 5 seconds")
    taskScheduler.schedule(
      { runScheduledJob() },
      Instant.now(clock).plus(Duration.ofSeconds(5)),
    )
  }

  @Scheduled(cron = "0 15 17 * * *")
  fun runDailyJob() {
    log.info("Running daily exchange rate retrieval job at 17:15")
    runScheduledJob()
  }

  private fun runScheduledJob() {
    jobExecutionService.executeJob(this)
    log.info("Completed exchange rate retrieval job")
  }

  override fun execute() {
    retrieveRates(Currency.GBP)
    retrieveRates(Currency.USD)
  }

  private fun retrieveRates(currency: Currency) {
    val startDate = exchangeRateService.findLatestRateDate(currency) ?: BACKFILL_START
    log.info("Retrieving ECB ${currency.name} rates from $startDate")
    val csv = ecbClient.fetchDailyRates(currency.name, startDate.toString(), CSV_FORMAT)
    val rates = EcbCsvParser.parse(csv)
    exchangeRateService.saveRates(currency, rates)
    log.info("Saved ${rates.size} ECB ${currency.name} rates")
  }
}
