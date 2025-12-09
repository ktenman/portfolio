package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.service.JobExecutionService
import ee.tenman.portfolio.service.PriceUpdateProcessor
import ee.tenman.portfolio.service.Trading212PriceUpdateService
import ee.tenman.portfolio.trading212.Trading212Service
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@ScheduledJob
class Trading212DataRetrievalJob(
  private val jobExecutionService: JobExecutionService,
  private val trading212Service: Trading212Service,
  private val trading212PriceUpdateService: Trading212PriceUpdateService,
  private val priceUpdateProcessor: PriceUpdateProcessor,
  private val clock: Clock,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)
  private val estonianZone = ZoneId.of("Europe/Tallinn")

  @Scheduled(cron = "0/10 * 10-18 * * MON-FRI", zone = "Europe/Tallinn")
  fun runJob() {
    if (!isWithinTradingHours()) {
      log.debug("Skipping Trading212 job - outside trading hours (10:00-18:30 EET/EEST on workdays)")
      return
    }

    log.info("Running Trading212 price update job")
    jobExecutionService.executeJob(this)
    log.info("Completed Trading212 price update job")
  }

  private fun isWithinTradingHours(): Boolean {
    val estonianTime = ZonedDateTime.now(clock.withZone(estonianZone))
    val dayOfWeek = estonianTime.dayOfWeek
    val currentTime = estonianTime.toLocalTime()

    if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
      return false
    }

    val startTime = LocalTime.of(10, 0)
    val endTime = LocalTime.of(18, 30)

    return !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime)
  }

  override fun execute() {
    priceUpdateProcessor.processPriceUpdates(
      platform = Platform.TRADING212,
      log = log,
      fetchPrices = { trading212Service.fetchCurrentPrices() },
      processSymbol = trading212PriceUpdateService::processSymbol,
    )
  }
}
