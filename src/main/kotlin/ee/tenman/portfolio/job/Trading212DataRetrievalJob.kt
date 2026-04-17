package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.pricing.PriceUpdateProcessor
import ee.tenman.portfolio.service.pricing.Trading212PriceUpdateService
import ee.tenman.portfolio.trading212.Trading212Service
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Duration
import java.time.Instant

@ScheduledJob
class Trading212DataRetrievalJob(
  private val jobExecutionService: JobExecutionService,
  private val trading212Service: Trading212Service,
  private val trading212PriceUpdateService: Trading212PriceUpdateService,
  private val priceUpdateProcessor: PriceUpdateProcessor,
  private val instrumentRepository: InstrumentRepository,
  private val taskScheduler: TaskScheduler,
  private val clock: Clock,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @PostConstruct
  fun scheduleInitialRun() {
    log.info("Scheduling initial Trading212 price update job to run in $INITIAL_DELAY_SECONDS seconds")
    taskScheduler.schedule(
      { runJob() },
      Instant.now(clock).plus(Duration.ofSeconds(INITIAL_DELAY_SECONDS)),
    )
  }

  @Scheduled(fixedDelayString = "\${scheduling.jobs.trading212-interval:60000}")
  fun runJob() {
    log.info("Running Trading212 price update job")
    jobExecutionService.executeJob(this)
    log.info("Completed Trading212 price update job")
  }

  override fun execute() {
    val eligibleSymbols =
      instrumentRepository
        .findByProviderName(ProviderName.TRADING212)
        .map { it.symbol }
        .toSet()
    priceUpdateProcessor.processPriceUpdates(
      platform = Platform.TRADING212,
      log = log,
      fetchPrices = { trading212Service.fetchCurrentPrices(eligibleSymbols) },
      processSymbol = trading212PriceUpdateService::processSymbol,
    )
  }

  companion object {
    private const val INITIAL_DELAY_SECONDS = 15L
  }
}
