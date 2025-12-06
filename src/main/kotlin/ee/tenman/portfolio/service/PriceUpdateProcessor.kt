package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.model.ProcessResult
import ee.tenman.portfolio.scheduler.MarketPhaseDetectionService
import org.slf4j.Logger
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

@Component
class PriceUpdateProcessor(
  private val marketPhaseDetectionService: MarketPhaseDetectionService,
  private val clock: Clock,
) {
  fun processPriceUpdates(
    platform: Platform,
    log: Logger,
    fetchPrices: () -> Map<String, BigDecimal>,
    processSymbol: (String, BigDecimal, Boolean, LocalDate) -> ProcessResult,
  ) {
    log.info("Starting ${platform.name} price update execution")
    val isWeekend = marketPhaseDetectionService.isWeekendPhase()

    if (isWeekend) {
      log.info("Skipping daily price save - weekend detected")
    }

    val prices = fetchPrices()
    val today = LocalDate.now(clock)

    var updatedCount = 0
    var dailyPricesSaved = 0
    var failedCount = 0

    prices.forEach { (symbol, price) ->
      val result = processSymbol(symbol, price, isWeekend, today)
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
        if (!isWeekend) ", saved $dailyPricesSaved ${platform.name} daily prices" else ""

    when {
      failedCount > 0 -> log.warn("$successMessage, $failedCount failed")
      else -> log.info("Successfully $successMessage")
    }
  }
}
