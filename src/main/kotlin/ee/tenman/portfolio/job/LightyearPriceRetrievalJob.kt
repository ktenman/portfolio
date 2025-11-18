package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.service.JobExecutionService
import ee.tenman.portfolio.service.LightyearPriceUpdateService
import ee.tenman.portfolio.service.PriceUpdateProcessor
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@Component
@ConditionalOnProperty(
  name = ["scheduling.enabled", "scheduling.jobs.lightyear-price-enabled"],
  havingValue = "true",
  matchIfMissing = false,
)
class LightyearPriceRetrievalJob(
  private val jobExecutionService: JobExecutionService,
  private val lightyearPriceService: LightyearPriceService,
  private val lightyearPriceUpdateService: LightyearPriceUpdateService,
  private val priceUpdateProcessor: PriceUpdateProcessor,
  private val clock: Clock,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)
  private val estonianZone = ZoneId.of("Europe/Tallinn")
  private lateinit var startupTime: Instant

  @PostConstruct
  fun init() {
    startupTime = Instant.now(clock)
    log.info("LightyearPriceRetrievalJob initialized. Will start polling after 4 minutes.")
  }

  @Scheduled(cron = "0/1 * 8-22 * * MON-FRI", zone = "Europe/Tallinn")
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

    if (minutesSinceStartup < 4) {
      log.debug(
        "Skipping job execution. Only {} minutes since startup. Waiting for 4 minutes.",
        minutesSinceStartup,
      )
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
    priceUpdateProcessor.processPriceUpdates(
      platform = Platform.LIGHTYEAR,
      log = log,
      fetchPrices = { lightyearPriceService.fetchCurrentPrices() },
      processSymbol = lightyearPriceUpdateService::processSymbol,
    )
  }
}
