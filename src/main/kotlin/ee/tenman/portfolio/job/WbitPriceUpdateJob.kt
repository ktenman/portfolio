package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.JobExecutionService
import ee.tenman.portfolio.service.WbitPriceUpdateService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@Component
@ConditionalOnProperty(
  name = ["scheduling.enabled", "scheduling.jobs.wbit-enabled"],
  havingValue = "true",
  matchIfMissing = false,
)
class WbitPriceUpdateJob(
  private val jobExecutionService: JobExecutionService,
  private val wbitPriceUpdateService: WbitPriceUpdateService,
  private val clock: Clock,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)
  private val estonianZone = ZoneId.of("Europe/Tallinn")

  @Scheduled(fixedDelayString = "\${scheduling.jobs.wbit-interval:30000}")
  fun runJob() {
    if (!isWithinOperatingHours()) {
      log.debug("Skipping WBIT job - outside operating hours (23:00-08:30 EET/EEST)")
      return
    }
    log.info("Running WBIT price update job")
    jobExecutionService.executeJob(this)
    log.info("Completed WBIT price update job")
  }

  fun isWithinOperatingHours(): Boolean {
    val estonianTime = ZonedDateTime.now(clock.withZone(estonianZone))
    val currentTime = estonianTime.toLocalTime()
    val afterEleven = !currentTime.isBefore(LocalTime.of(23, 0))
    val beforeEightThirty = !currentTime.isAfter(LocalTime.of(8, 30))
    return afterEleven || beforeEightThirty
  }

  override fun execute() {
    wbitPriceUpdateService.updateWbitPrice()
  }
}
