package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.exception.PriceRefreshException
import ee.tenman.portfolio.model.CollectionRun
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
    processSymbol: (String, BigDecimal, Boolean, LocalDate) -> Boolean,
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
    val requested = expectedSymbols?.size ?: prices.size
    val today = LocalDate.now(clock)

    var updatedCount = 0
    var failedCount = 0

    prices.forEach { (symbol, price) ->
      if (price <= BigDecimal.ZERO) {
        run?.failed(symbol, IllegalArgumentException("Nonpositive price for $symbol"))
        failedCount++
        return@forEach
      }
      run?.fetched(symbol)
      runCatching { processSymbol(symbol, price, isWeekend, today) }
        .onSuccess { changed ->
          updatedCount++
          run?.persisted(symbol, changed)
        }.onFailure {
          log.warn("Failed to persist $platform price for $symbol: ${it.message}")
          run?.failed(symbol, it)
          failedCount++
        }
    }

    if (updatedCount == requested && failedCount == 0) {
      log.info("Successfully Updated current prices for $updatedCount/${prices.size} instruments")
      return
    }
    val summary =
      "$platform price refresh incomplete: requested=$requested, fetched=${prices.size}, " +
        "persisted=$updatedCount, failed=${requested - updatedCount}"
    if (updatedCount == 0) throw PriceRefreshException(summary)
    log.warn(summary)
  }

  fun processSymbolUpdate(
    symbol: String,
    price: BigDecimal,
    isWeekend: Boolean,
    today: LocalDate,
    provider: ProviderName,
  ): Boolean {
    val instrument = instrumentService.findBySymbol(symbol)
    val changed = instrumentService.updateCurrentPrice(instrument.id, price)
    priceSnapshotService.saveSnapshot(instrument, price, provider)
    log.debug("Updated current price for $symbol: $price")
    if (isWeekend) return changed
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
    return changed
  }
}
