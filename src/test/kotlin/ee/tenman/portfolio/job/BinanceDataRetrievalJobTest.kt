package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.binance.BinanceService
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.pricing.PriceSnapshotBackfillService
import ee.tenman.portfolio.service.pricing.PriceSnapshotService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TreeMap

class BinanceDataRetrievalJobTest {
  private val instrumentService: InstrumentService = mockk(relaxed = true)
  private val jobExecutionService: JobExecutionService = mockk(relaxed = true)
  private val binanceService: BinanceService = mockk()
  private val dataProcessingUtil: DataProcessingUtil = mockk(relaxed = true)
  private val dailyPriceService: DailyPriceService = mockk(relaxed = true)
  private val priceSnapshotService: PriceSnapshotService = mockk(relaxed = true)
  private val priceSnapshotBackfillService: PriceSnapshotBackfillService = mockk(relaxed = true)
  private val fixedInstant: Instant = Instant.parse("2025-12-23T10:00:00Z")
  private val clock: Clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

  private val job =
    BinanceDataRetrievalJob(
      instrumentService = instrumentService,
      jobExecutionService = jobExecutionService,
      binanceService = binanceService,
      dataProcessingUtil = dataProcessingUtil,
      dailyPriceService = dailyPriceService,
      priceSnapshotService = priceSnapshotService,
      priceSnapshotBackfillService = priceSnapshotBackfillService,
      clock = clock,
    )

  @Test
  fun `should refresh current price when historical data exists`() {
    val instrument = createInstrument("BTCEUR")
    val currentPrice = BigDecimal("95000.50")
    val expectedDate = LocalDate.of(2025, 12, 23)
    every { instrumentService.getInstrumentsByProvider(ProviderName.BINANCE) } returns listOf(instrument)
    every { dailyPriceService.hasHistoricalData(instrument) } returns true
    every { binanceService.getCurrentPrice("BTCEUR") } returns currentPrice

    job.execute()

    verify(exactly = 1) { binanceService.getCurrentPrice("BTCEUR") }
    verify(exactly = 1) {
      dailyPriceService.saveCurrentPrice(instrument, currentPrice, expectedDate, ProviderName.BINANCE)
    }
    verify(exactly = 1) { priceSnapshotService.saveSnapshot(instrument, currentPrice, ProviderName.BINANCE) }
    verify(exactly = 1) { priceSnapshotBackfillService.backfillFromBinance(instrument) }
    verify(exactly = 1) { instrumentService.updateCurrentPrice(1L, currentPrice) }
    verify(exactly = 0) { binanceService.getDailyPricesAsync(any()) }
  }

  @Test
  fun `should fetch full history when no historical data exists`() {
    val instrument = createInstrument("BNBEUR")
    every { instrumentService.getInstrumentsByProvider(ProviderName.BINANCE) } returns listOf(instrument)
    every { dailyPriceService.hasHistoricalData(instrument) } returns false
    every { binanceService.getDailyPricesAsync("BNBEUR") } returns TreeMap()

    job.execute()

    verify(exactly = 1) { binanceService.getDailyPricesAsync("BNBEUR") }
    verify(exactly = 0) { binanceService.getCurrentPrice(any()) }
  }

  @Test
  fun `should skip when no instruments found`() {
    every { instrumentService.getInstrumentsByProvider(ProviderName.BINANCE) } returns emptyList()

    job.execute()

    verify(exactly = 0) { binanceService.getCurrentPrice(any()) }
    verify(exactly = 0) { binanceService.getDailyPricesAsync(any()) }
  }

  @Test
  fun `should handle errors gracefully and continue processing`() {
    val btc = createInstrument("BTCEUR", id = 1L)
    val bnb = createInstrument("BNBEUR", id = 2L)
    every { instrumentService.getInstrumentsByProvider(ProviderName.BINANCE) } returns listOf(btc, bnb)
    every { dailyPriceService.hasHistoricalData(btc) } returns true
    every { dailyPriceService.hasHistoricalData(bnb) } returns true
    every { binanceService.getCurrentPrice("BTCEUR") } throws RuntimeException("API error")
    every { binanceService.getCurrentPrice("BNBEUR") } returns BigDecimal("750.00")

    job.execute()

    verify(exactly = 1) { binanceService.getCurrentPrice("BTCEUR") }
    verify(exactly = 1) { binanceService.getCurrentPrice("BNBEUR") }
    verify(exactly = 1) { instrumentService.updateCurrentPrice(2L, BigDecimal("750.00")) }
  }

  @Test
  fun `should call backfill on every execution relying on service-level dedup`() {
    val instrument = createInstrument("BTCEUR")
    val currentPrice = BigDecimal("95000.50")
    every { instrumentService.getInstrumentsByProvider(ProviderName.BINANCE) } returns listOf(instrument)
    every { dailyPriceService.hasHistoricalData(instrument) } returns true
    every { binanceService.getCurrentPrice("BTCEUR") } returns currentPrice

    job.execute()
    job.execute()

    verify(exactly = 2) { priceSnapshotBackfillService.backfillFromBinance(instrument) }
    verify(exactly = 2) { priceSnapshotService.saveSnapshot(instrument, currentPrice, ProviderName.BINANCE) }
  }

  @Test
  fun `should have correct job name`() {
    expect(job.getName()).toEqual("BinanceDataRetrievalJob")
  }

  private fun createInstrument(
    symbol: String,
    providerName: ProviderName = ProviderName.BINANCE,
    id: Long = 1L,
  ): Instrument =
    mockk {
      every { this@mockk.id } returns id
      every { this@mockk.symbol } returns symbol
      every { this@mockk.providerName } returns providerName
    }
}
