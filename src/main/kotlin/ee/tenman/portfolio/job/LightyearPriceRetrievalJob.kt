package ee.tenman.portfolio.job

import ee.tenman.portfolio.configuration.LightyearScrapingProperties
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.model.CollectionSchedules
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.monitoring.CollectionMonitorService
import ee.tenman.portfolio.service.pricing.LightyearPriceUpdateService
import ee.tenman.portfolio.service.pricing.PriceUpdateProcessor
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@ScheduledJob
class LightyearPriceRetrievalJob(
  private val jobExecutionService: JobExecutionService,
  private val lightyearPriceService: LightyearPriceService,
  private val lightyearPriceUpdateService: LightyearPriceUpdateService,
  private val priceUpdateProcessor: PriceUpdateProcessor,
  private val clock: Clock,
  private val properties: LightyearScrapingProperties,
  private val collectionMonitor: CollectionMonitorService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)
  private val estonianZone = ZoneId.of("Europe/Tallinn")
  private lateinit var startupTime: Instant

  @PostConstruct
  fun init() {
    startupTime = Instant.now(clock)
    log.info("LightyearPriceRetrievalJob initialized. Will start polling after 4 minutes.")
  }

  @Scheduled(cron = CollectionSchedules.LIGHTYEAR_PRICE_CRON, zone = CollectionSchedules.TIME_ZONE)
  fun runJob() {
    if (!shouldRun()) {
      return
    }

    log.info("Running Lightyear price update job")
    jobExecutionService.executeJob(this)
    log.info("Completed Lightyear price update job")
  }

  private fun shouldRun(): Boolean {
    val now = Instant.now(clock)
    val minutesSinceStartup =
      java.time.Duration
      .between(startupTime, now)
      .toMinutes()

    if (minutesSinceStartup < CollectionSchedules.LIGHTYEAR_PRICE_STARTUP_SECONDS / 60) {
      log.debug("Skipping job execution. Only $minutesSinceStartup minutes since startup. Waiting for 4 minutes.")
      return false
    }

    val estonianTime = ZonedDateTime.now(clock.withZone(estonianZone))
    val dayOfWeek = estonianTime.dayOfWeek

    if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
      return false
    }

    return true
  }

  override fun execute() {
    val symbols = properties.getAllSymbols().toSet()
    collectionMonitor.collect(CollectionKey.LIGHTYEAR_PRICES, symbols) { run ->
      priceUpdateProcessor.processPriceUpdates(
        platform = Platform.LIGHTYEAR,
        log = log,
        fetchPrices = { lightyearPriceService.fetchCurrentPrices(run::failed) },
        processSymbol = lightyearPriceUpdateService::processSymbol,
        expectedSymbols = symbols,
        run = run,
      )
    }
  }
}
