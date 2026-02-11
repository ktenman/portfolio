package ee.tenman.portfolio.service.pricing

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.model.ProcessResult
import ee.tenman.portfolio.scheduler.MarketPhaseDetectionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.createInstrument
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PriceUpdateProcessorTest {
  private val marketPhaseDetectionService = mockk<MarketPhaseDetectionService>()
  private val instrumentService = mockk<InstrumentService>()
  private val dailyPriceService = mockk<DailyPriceService>()
  private val priceSnapshotService = mockk<PriceSnapshotService>()
  private val clock = Clock.fixed(Instant.parse("2024-01-15T12:00:00Z"), ZoneId.systemDefault())
  private val log = mockk<Logger>(relaxed = true)

  private val processor =
    PriceUpdateProcessor(marketPhaseDetectionService, clock, instrumentService, dailyPriceService, priceSnapshotService)

  @Test
  fun `processPriceUpdates should process all symbols successfully on weekday`() {
    every { marketPhaseDetectionService.isWeekendPhase() } returns false

    val prices = mapOf("AAPL" to BigDecimal("150.00"), "GOOGL" to BigDecimal("2800.00"))
    val processedSymbols = mutableListOf<String>()

    processor.processPriceUpdates(
      platform = Platform.LIGHTYEAR,
      log = log,
      fetchPrices = { prices },
      processSymbol = { symbol, _, _, _ ->
        processedSymbols.add(symbol)
        ProcessResult.SUCCESS_WITH_DAILY_PRICE
      },
    )

    expect(processedSymbols).toContainExactly("AAPL", "GOOGL")
    verify { log.info("Starting LIGHTYEAR price update execution") }
    verify { log.info(match { it.contains("Updated current prices for 2/2 instruments") }) }
    verify { log.info(match { it.contains("saved 2 LIGHTYEAR daily prices") }) }
  }

  @Test
  fun `processPriceUpdates should skip daily price save on weekend`() {
    every { marketPhaseDetectionService.isWeekendPhase() } returns true

    val prices = mapOf("AAPL" to BigDecimal("150.00"))

    processor.processPriceUpdates(
      platform = Platform.LIGHTYEAR,
      log = log,
      fetchPrices = { prices },
      processSymbol = { _, _, isWeekend, _ ->
        expect(isWeekend).toEqual(true)
        ProcessResult.SUCCESS_WITHOUT_DAILY_PRICE
      },
    )

    verify { log.info("Skipping daily price save - weekend detected") }
    verify(exactly = 0) { log.info(match { it.contains("saved") && it.contains("daily prices") }) }
  }

  @Test
  fun `processPriceUpdates should warn on failures`() {
    every { marketPhaseDetectionService.isWeekendPhase() } returns false

    val prices = mapOf("AAPL" to BigDecimal("150.00"), "INVALID" to BigDecimal("0.00"))

    processor.processPriceUpdates(
      platform = Platform.TRADING212,
      log = log,
      fetchPrices = { prices },
      processSymbol = { symbol, _, _, _ ->
        if (symbol == "INVALID") ProcessResult.FAILED else ProcessResult.SUCCESS_WITH_DAILY_PRICE
      },
    )

    verify { log.warn(match { it.contains("1 failed") }) }
  }

  @Test
  fun `processPriceUpdates should pass correct date to processSymbol`() {
    every { marketPhaseDetectionService.isWeekendPhase() } returns false

    val prices = mapOf("AAPL" to BigDecimal("150.00"))
    val expectedDate = LocalDate.now(clock)
    var capturedDate: LocalDate? = null

    processor.processPriceUpdates(
      platform = Platform.TRADING212,
      log = log,
      fetchPrices = { prices },
      processSymbol = { _, _, _, date ->
        capturedDate = date
        ProcessResult.SUCCESS_WITH_DAILY_PRICE
      },
    )

    expect(capturedDate).toEqual(expectedDate)
  }

  @Test
  fun `processPriceUpdates should count results correctly with mixed outcomes`() {
    every { marketPhaseDetectionService.isWeekendPhase() } returns false

    val prices =
      mapOf(
        "SUCCESS_WITH_DAILY" to BigDecimal("100.00"),
        "SUCCESS_WITHOUT_DAILY" to BigDecimal("200.00"),
        "FAILED" to BigDecimal("300.00"),
      )

    processor.processPriceUpdates(
      platform = Platform.BINANCE,
      log = log,
      fetchPrices = { prices },
      processSymbol = { symbol, _, _, _ ->
        when (symbol) {
          "SUCCESS_WITH_DAILY" -> ProcessResult.SUCCESS_WITH_DAILY_PRICE
          "SUCCESS_WITHOUT_DAILY" -> ProcessResult.SUCCESS_WITHOUT_DAILY_PRICE
          else -> ProcessResult.FAILED
        }
      },
    )

    verify { log.warn(match { it.contains("Updated current prices for 2/3 instruments") && it.contains("1 failed") }) }
  }

  @Test
  fun `processSymbolUpdate should save price snapshot`() {
    val instrument = createInstrument()
    val price = BigDecimal("155.00")
    val today = LocalDate.of(2024, 1, 15)
    every { instrumentService.findBySymbol("AAPL") } returns instrument
    every { instrumentService.updateCurrentPrice(1L, price) } just runs
    every { priceSnapshotService.saveSnapshot(instrument, price, ProviderName.FT) } just runs
    every { dailyPriceService.saveDailyPrice(any()) } just runs

    val result = processor.processSymbolUpdate("AAPL", price, false, today, ProviderName.FT)

    expect(result).toEqual(ProcessResult.SUCCESS_WITH_DAILY_PRICE)
    verify(exactly = 1) { priceSnapshotService.saveSnapshot(instrument, price, ProviderName.FT) }
  }

  @Test
  fun `processSymbolUpdate should continue when snapshot save fails`() {
    val instrument = createInstrument()
    val price = BigDecimal("155.00")
    val today = LocalDate.of(2024, 1, 15)
    every { instrumentService.findBySymbol("AAPL") } returns instrument
    every { instrumentService.updateCurrentPrice(1L, price) } just runs
    every { priceSnapshotService.saveSnapshot(instrument, price, ProviderName.FT) } throws RuntimeException("DB error")
    every { dailyPriceService.saveDailyPrice(any()) } just runs

    val result = processor.processSymbolUpdate("AAPL", price, false, today, ProviderName.FT)

    expect(result).toEqual(ProcessResult.SUCCESS_WITH_DAILY_PRICE)
  }
}
