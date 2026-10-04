package ee.tenman.portfolio.service.pricing

import ch.tutteli.atrium.api.fluent.en_GB.notToThrow
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.exception.PriceRefreshException
import ee.tenman.portfolio.model.CollectionRun
import ee.tenman.portfolio.model.CollectionRunResult
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
  fun `should fail the refresh when no fetched prices can be persisted`() {
    every { marketPhaseDetectionService.isWeekendPhase() } returns false

    expect {
      processor.processPriceUpdates(
        platform = Platform.LIGHTYEAR,
        log = log,
        fetchPrices = { mapOf("VGLA:GER:EUR" to BigDecimal("4.38")) },
        processSymbol = { _, _, _, _ -> error("Price persistence failed") },
      )
    }.toThrow<IllegalStateException>()
  }

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
        true
      },
    )

    expect(processedSymbols).toContainExactly("AAPL", "GOOGL")
    verify { log.info("Starting LIGHTYEAR price update execution") }
    verify { log.info(match { it.contains("Updated current prices for 2/2 instruments") }) }
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
        false
      },
    )

    verify { log.info("Skipping daily price save - weekend detected") }
  }

  @Test
  fun `should finish when one price is invalid and the rest persist`() {
    every { marketPhaseDetectionService.isWeekendPhase() } returns false

    val prices = mapOf("AAPL" to BigDecimal("150.00"), "INVALID" to BigDecimal("0.00"))

    expect {
      processor.processPriceUpdates(
        platform = Platform.TRADING212,
        log = log,
        fetchPrices = { prices },
        processSymbol = { symbol, _, _, _ ->
          if (symbol == "INVALID") error("Price persistence failed") else true
        },
      )
    }.notToThrow()
  }

  @Test
  fun `should continue persisting prices after a transaction throws`() {
    every { marketPhaseDetectionService.isWeekendPhase() } returns false
    val persisted = mutableListOf<String>()
    processor.processPriceUpdates(
      platform = Platform.LIGHTYEAR,
      log = log,
      fetchPrices = { mapOf("VGLA" to BigDecimal("4.38"), "WEBN" to BigDecimal("13.12")) },
      processSymbol = { symbol, _, _, _ ->
        if (symbol == "VGLA") error("Transaction commit failed")
        persisted.add(symbol)
        true
      },
    )
    expect(persisted).toContainExactly("WEBN")
  }

  @Test
  fun `should fail when no price could be persisted`() {
    every { marketPhaseDetectionService.isWeekendPhase() } returns false
    expect {
      processor.processPriceUpdates(
        platform = Platform.LIGHTYEAR,
        log = log,
        fetchPrices = { mapOf("VGLA" to BigDecimal("4.38")) },
        processSymbol = { _, _, _, _ -> error("Transaction commit failed") },
      )
    }.toThrow<PriceRefreshException>()
  }

  @Test
  fun `should count only validated fetched prices and committed updates`() {
    every { marketPhaseDetectionService.isWeekendPhase() } returns false
    val symbols = setOf("GOOD", "ZERO", "MISSING")
    val run = CollectionRun(symbols)

    runCatching {
      processor.processPriceUpdates(
        platform = Platform.LIGHTYEAR,
        log = log,
        fetchPrices = { mapOf("GOOD" to BigDecimal.ONE, "ZERO" to BigDecimal.ZERO) },
        processSymbol = { _, _, _, _ -> true },
        expectedSymbols = symbols,
        run = run,
      )
    }

    expect(run.result().persisted).toContainExactly("GOOD")
    expect(run.result().fetched).toContainExactly("GOOD")
    expect(run.result().failed).toContainExactly("ZERO", "MISSING")
  }

  @Test
  fun `should count an unchanged price as persisted`() {
    expect(refresh(changed = false).persisted).toContainExactly("Ärikinnisvara")
  }

  @Test
  fun `should not record an unchanged price as changed`() {
    expect(refresh(changed = false).changed).toEqual(emptySet())
  }

  @Test
  fun `should record a changed price as changed`() {
    expect(refresh(changed = true).changed).toContainExactly("Ärikinnisvara")
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
        true
      },
    )

    expect(capturedDate).toEqual(expectedDate)
  }

  @Test
  fun `processPriceUpdates should count every failed price when nothing persists`() {
    every { marketPhaseDetectionService.isWeekendPhase() } returns false

    val prices =
      mapOf(
        "ZERO" to BigDecimal("0.00"),
        "FAILED" to BigDecimal("300.00"),
        "KATKI" to BigDecimal("200.00"),
      )

    val failure =
      runCatching {
        processor.processPriceUpdates(
          platform = Platform.BINANCE,
          log = log,
          fetchPrices = { prices },
          processSymbol = { _, _, _, _ -> error("Price persistence failed") },
        )
      }.exceptionOrNull()

    expect(failure?.message).toEqual("BINANCE price refresh incomplete: requested=3, fetched=3, persisted=0, failed=3")
  }

  @Test
  fun `processSymbolUpdate should save price snapshot`() {
    val instrument = createInstrument()
    val price = BigDecimal("155.00")
    val today = LocalDate.of(2024, 1, 15)
    every { instrumentService.findBySymbol("AAPL") } returns instrument
    every { instrumentService.updateCurrentPrice(1L, price) } returns true
    every { priceSnapshotService.saveSnapshot(instrument, price, ProviderName.FT) } just runs
    every { dailyPriceService.saveDailyPrice(any()) } just runs

    val result = processor.processSymbolUpdate("AAPL", price, false, today, ProviderName.FT)

    expect(result).toEqual(true)
    verify(exactly = 1) { priceSnapshotService.saveSnapshot(instrument, price, ProviderName.FT) }
  }

  @Test
  fun `processSymbolUpdate should report an unchanged price`() {
    expect(update(isWeekend = false)).toEqual(false)
  }

  @Test
  fun `processSymbolUpdate should report an unchanged price on a weekend`() {
    expect(update(isWeekend = true)).toEqual(false)
  }

  @Test
  fun `processSymbolUpdate should still save the snapshot and daily price for an unchanged price`() {
    update(isWeekend = false)
    verify { priceSnapshotService.saveSnapshot(any(), BigDecimal("155.00"), ProviderName.FT) }
    verify { dailyPriceService.saveDailyPrice(match { it.closePrice.compareTo(BigDecimal("155.00")) == 0 }) }
  }

  @Test
  fun `processSymbolUpdate should throw when snapshot save fails`() {
    val instrument = createInstrument()
    val price = BigDecimal("155.00")
    val today = LocalDate.of(2024, 1, 15)
    every { instrumentService.findBySymbol("AAPL") } returns instrument
    every { instrumentService.updateCurrentPrice(1L, price) } returns true
    every { priceSnapshotService.saveSnapshot(instrument, price, ProviderName.FT) } throws RuntimeException("DB error")
    every { dailyPriceService.saveDailyPrice(any()) } just runs

    expect { processor.processSymbolUpdate("AAPL", price, false, today, ProviderName.FT) }
      .toThrow<RuntimeException>()
  }

  private fun refresh(changed: Boolean): CollectionRunResult {
    every { marketPhaseDetectionService.isWeekendPhase() } returns false
    val run = CollectionRun(setOf("Ärikinnisvara"))
    processor.processPriceUpdates(
      platform = Platform.LIGHTYEAR,
      log = log,
      fetchPrices = { mapOf("Ärikinnisvara" to BigDecimal("4.38")) },
      processSymbol = { _, _, _, _ -> changed },
      expectedSymbols = setOf("Ärikinnisvara"),
      run = run,
    )
    return run.result()
  }

  private fun update(isWeekend: Boolean): Boolean {
    val instrument = createInstrument()
    val price = BigDecimal("155.00")
    every { instrumentService.findBySymbol("AAPL") } returns instrument
    every { instrumentService.updateCurrentPrice(1L, price) } returns false
    every { priceSnapshotService.saveSnapshot(instrument, price, ProviderName.FT) } just runs
    if (!isWeekend) every { dailyPriceService.saveDailyPrice(any()) } just runs
    return processor.processSymbolUpdate("AAPL", price, isWeekend, LocalDate.of(2024, 1, 15), ProviderName.FT)
  }
}
