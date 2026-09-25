package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.exception.PriceRefreshException
import ee.tenman.portfolio.model.CollectionRun
import ee.tenman.portfolio.model.ProcessResult
import ee.tenman.portfolio.scheduler.MarketPhaseDetectionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

@Component
class PriceUpdateProcessor(
  private val marketPhaseDetectionService: MarketPhaseDetectionService,
  private val clock: Clock,
  private val instrumentService: InstrumentService,
  private val dailyPriceService: DailyPriceService,
  private val priceSnapshotService: PriceSnapshotService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun processPriceUpdates(
    platform: Platform,
    log: Logger,
    fetchPrices: () -> Map<String, BigDecimal>,
    processSymbol: (String, BigDecimal, Boolean, LocalDate) -> ProcessResult,
    expectedCount: Int? = null,
    expectedSymbols: Set<String>? = null,
    run: CollectionRun? = null,
  ) {
    log.info("Starting ${platform.name} price update execution")
    val isWeekend = marketPhaseDetectionService.isWeekendPhase()

    if (isWeekend) {
      log.info("Skipping daily price save - weekend detected")
    }

    expectedSymbols?.forEach { run?.attempted(it) }
    val prices = fetchPrices()
    val requested = expectedSymbols?.size ?: expectedCount ?: prices.size
    val today = LocalDate.now(clock)

    var updatedCount = 0
    var dailyPricesSaved = 0
    var failedCount = 0

    prices.forEach { (symbol, price) ->
      if (price <= BigDecimal.ZERO) {
        run?.failed(symbol, IllegalArgumentException("Nonpositive price for $symbol"))
        failedCount++
        return@forEach
      }
      run?.attempted(symbol)
      run?.fetched(symbol)
      val result =
        runCatching { processSymbol(symbol, price, isWeekend, today) }.getOrElse {
          log.warn("Failed to persist $platform price for $symbol: ${it.message}")
          run?.failed(symbol, it)
          ProcessResult.FAILED
        }
      when (result) {
        ProcessResult.SUCCESS_WITH_DAILY_PRICE -> {
          updatedCount++
          dailyPricesSaved++
          run?.persisted(symbol)
        }

        ProcessResult.SUCCESS_WITHOUT_DAILY_PRICE -> {
          updatedCount++
          run?.persisted(symbol)
        }
        ProcessResult.FAILED -> {
          failedCount++
          run?.failed(symbol, IllegalStateException("Price persistence failed for $symbol"))
        }
      }
    }

    val successMessage =
      "Updated current prices for $updatedCount/${prices.size} instruments" +
        if (!isWeekend) ", saved $dailyPricesSaved ${platform.name} daily prices" else ""

    if (updatedCount < requested || failedCount > 0) {
      throw PriceRefreshException(
        "$platform price refresh incomplete: requested=$requested, fetched=${prices.size}, " +
          "persisted=$updatedCount, failed=${requested - updatedCount}",
      )
    }
    log.info("Successfully $successMessage")
  }

  fun processSymbolUpdate(
    symbol: String,
    price: BigDecimal,
    isWeekend: Boolean,
    today: LocalDate,
    provider: ProviderName,
  ): ProcessResult {
    val instrument = instrumentService.findBySymbol(symbol)
    instrumentService.updateCurrentPrice(instrument.id, price)
    priceSnapshotService.saveSnapshot(instrument, price, provider)
    log.debug("Updated current price for $symbol: $price")
    if (isWeekend) return ProcessResult.SUCCESS_WITHOUT_DAILY_PRICE
    val dailyPrice =
      DailyPrice(
        instrument = instrument,
        entryDate = today,
        providerName = provider,
        openPrice = price,
        highPrice = price,
        lowPrice = price,
        closePrice = price,
        volume = null,
      )
    dailyPriceService.saveDailyPrice(dailyPrice)
    log.debug("Saved $provider daily price for $symbol: $price")
    return ProcessResult.SUCCESS_WITH_DAILY_PRICE
  }
}
