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
import java.time.LocalDate

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

  @Scheduled(fixedDelayString = "\${scheduling.jobs.trading212-interval:30000}")
  fun runJob() {
    log.info("Running Trading212 price update job")
    jobExecutionService.executeJob(this)
    log.info("Completed Trading212 price update job")
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

    if (failedCount > 0) {
      log.warn("$successMessage, $failedCount failed")
    } else {
      log.info("Successfully $successMessage")
    }
  }
}
