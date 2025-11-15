package ee.tenman.portfolio.job

import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.scheduler.MarketPhase
import ee.tenman.portfolio.scheduler.MarketPhaseDetectionService
import ee.tenman.portfolio.service.JobExecutionService
import ee.tenman.portfolio.service.LightyearPriceUpdateService
import ee.tenman.portfolio.service.LightyearPriceUpdateService.ProcessResult
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
  name = ["scheduling.enabled", "scheduling.jobs.lightyear-price-enabled"],
  havingValue = "true",
  matchIfMissing = false,
)
class LightyearPriceRetrievalJob(
  private val jobExecutionService: JobExecutionService,
  private val lightyearPriceService: LightyearPriceService,
  private val lightyearPriceUpdateService: LightyearPriceUpdateService,
  private val marketPhaseDetectionService: MarketPhaseDetectionService,
  private val clock: Clock,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)
  private val estonianZone = ZoneId.of("Europe/Tallinn")

  @Scheduled(cron = "*/5 * 8-22 * * MON-FRI", zone = "Europe/Tallinn")
  fun runJob() {
    if (!shouldRun()) {
      return
    }

    log.info("Running Lightyear price update job")
    jobExecutionService.executeJob(this)
    log.info("Completed Lightyear price update job")
  }

  private fun shouldRun(): Boolean {
    val estonianTime = ZonedDateTime.now(clock.withZone(estonianZone))
    val dayOfWeek = estonianTime.dayOfWeek
    val currentTime = estonianTime.toLocalTime()
    val currentSecond = currentTime.second

    if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
      return false
    }

    val preMarketStart = LocalTime.of(8, 30)
    val mainMarketStart = LocalTime.of(10, 0)
    val mainMarketEnd = LocalTime.of(18, 30)
    val postMarketEnd = LocalTime.of(23, 0)

    return when {
      currentTime.isBefore(preMarketStart) -> false
      !currentTime.isBefore(postMarketEnd) -> false
      currentTime.isBefore(mainMarketStart) -> true
      currentTime.isBefore(mainMarketEnd) -> currentSecond in listOf(5, 15, 25, 35, 45, 55)
      else -> true
    }
  }

  override fun execute() {
    log.info("Starting Lightyear price update execution")
    val marketPhase = marketPhaseDetectionService.detectMarketPhase()
    val isWeekend = marketPhase == MarketPhase.WEEKEND

    if (isWeekend) {
      log.info("Skipping daily price save - weekend detected")
    }

    val prices = lightyearPriceService.fetchCurrentPrices()
    val today = LocalDate.now(clock)

    var updatedCount = 0
    var dailyPricesSaved = 0
    var failedCount = 0

    prices.forEach { (symbol, price) ->
      val result = lightyearPriceUpdateService.processSymbol(symbol, price, isWeekend, today)
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
        if (!isWeekend) ", saved $dailyPricesSaved Lightyear daily prices" else ""

    if (failedCount > 0) {
      log.warn("$successMessage, $failedCount failed")
    } else {
      log.info("Successfully $successMessage")
    }
  }
}
