package ee.tenman.portfolio.job

import ee.tenman.portfolio.scheduler.MarketPhase
import ee.tenman.portfolio.scheduler.MarketPhaseDetectionService
import ee.tenman.portfolio.service.JobExecutionService
import ee.tenman.portfolio.service.Trading212PriceUpdateService
import ee.tenman.portfolio.service.Trading212PriceUpdateService.ProcessResult
import ee.tenman.portfolio.trading212.Trading212Service
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@Component
@ConditionalOnProperty(
  name = ["scheduling.enabled", "scheduling.jobs.trading212-enabled"],
  havingValue = "true",
  matchIfMissing = false,
)
class Trading212DataRetrievalJob(
  private val jobExecutionService: JobExecutionService,
  private val trading212Service: Trading212Service,
  private val trading212PriceUpdateService: Trading212PriceUpdateService,
  private val marketPhaseDetectionService: MarketPhaseDetectionService,
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
    log.info("Starting Trading212 price update execution")
    val marketPhase = marketPhaseDetectionService.detectMarketPhase()
    val isWeekend = marketPhase == MarketPhase.WEEKEND

    if (isWeekend) {
      log.info("Skipping daily price save - weekend detected")
    }

    val prices = trading212Service.fetchCurrentPrices()
    val today = LocalDate.now(clock)

    var updatedCount = 0
    var dailyPricesSaved = 0
    var failedCount = 0

    prices.forEach { (symbol, price) ->
      val result = trading212PriceUpdateService.processSymbol(symbol, price, isWeekend, today)
      when (result) {
        ProcessResult.SUCCESS_WITH_DAILY_PRICE -> {
          updatedCount++
          dailyPricesSaved++
        }
        ProcessResult.SUCCESS_WITHOUT_DAILY_PRICE -> updatedCount++
        ProcessResult.FAILED -> failedCount++
      }
    }

    val successMessage =
      "Updated current prices for $updatedCount/${prices.size} instruments" +
        if (!isWeekend) ", saved $dailyPricesSaved Trading212 daily prices" else ""

    when {
      failedCount > 0 -> log.warn("$successMessage, $failedCount failed")
      else -> log.info("Successfully $successMessage")
    }
  }
}
