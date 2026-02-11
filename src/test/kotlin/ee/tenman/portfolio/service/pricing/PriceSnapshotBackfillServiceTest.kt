package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.binance.BinanceService
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.createInstrument
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.TreeMap

class PriceSnapshotBackfillServiceTest {
  private val binanceService = mockk<BinanceService>()
  private val priceSnapshotService = mockk<PriceSnapshotService>()
  private val snapshotBackfillCacheService = mockk<SnapshotBackfillCacheService>()
  private lateinit var service: PriceSnapshotBackfillService
  private lateinit var instrument: Instrument

  @BeforeEach
  fun setUp() {
    clearMocks(binanceService, priceSnapshotService, snapshotBackfillCacheService)
    service = PriceSnapshotBackfillService(binanceService, priceSnapshotService, snapshotBackfillCacheService)
    instrument =
      createInstrument(
        symbol = "BTCEUR",
        name = "Bitcoin EUR",
        category = "Crypto",
        baseCurrency = "EUR",
        currentPrice = BigDecimal("50000.00"),
        providerName = ProviderName.BINANCE,
      )
  }

  @Test
  fun `should backfill hourly snapshots from Binance API`() {
    val hour1 = Instant.parse("2024-01-15T08:00:00Z")
    val hour2 = Instant.parse("2024-01-15T09:00:00Z")
    val hourlyPrices = TreeMap<Instant, BigDecimal>()
    hourlyPrices[hour1] = BigDecimal("49000.00")
    hourlyPrices[hour2] = BigDecimal("50000.00")
    every { snapshotBackfillCacheService.isBackfilled(1L) } returns null
    every { priceSnapshotService.hasSnapshots(1L, instrument.providerName) } returns false
    every { binanceService.getHourlyPrices("BTCEUR", 48) } returns hourlyPrices
    every { priceSnapshotService.saveSnapshots(1L, instrument.providerName, hourlyPrices) } just runs
    every { snapshotBackfillCacheService.markBackfilled(1L) } returns true

    service.backfillFromBinance(instrument)

    verify(exactly = 1) { priceSnapshotService.saveSnapshots(1L, instrument.providerName, hourlyPrices) }
    verify(exactly = 1) { snapshotBackfillCacheService.markBackfilled(1L) }
  }

  @Test
  fun `should skip backfill when snapshots already exist`() {
    every { snapshotBackfillCacheService.isBackfilled(1L) } returns null
    every { priceSnapshotService.hasSnapshots(1L, instrument.providerName) } returns true
    every { snapshotBackfillCacheService.markBackfilled(1L) } returns true

    service.backfillFromBinance(instrument)

    verify(exactly = 0) { binanceService.getHourlyPrices(any(), any()) }
    verify(exactly = 0) { priceSnapshotService.saveSnapshots(any(), any(), any()) }
    verify(exactly = 1) { snapshotBackfillCacheService.markBackfilled(1L) }
  }

  @Test
  fun `should skip saving and caching when Binance returns empty hourly prices`() {
    every { snapshotBackfillCacheService.isBackfilled(1L) } returns null
    every { priceSnapshotService.hasSnapshots(1L, instrument.providerName) } returns false
    every { binanceService.getHourlyPrices("BTCEUR", 48) } returns TreeMap()

    service.backfillFromBinance(instrument)

    verify(exactly = 0) { priceSnapshotService.saveSnapshots(any(), any(), any()) }
    verify(exactly = 0) { snapshotBackfillCacheService.markBackfilled(any()) }
  }

  @Test
  fun `should skip entirely when cache indicates already backfilled`() {
    every { snapshotBackfillCacheService.isBackfilled(1L) } returns true

    service.backfillFromBinance(instrument)

    verify(exactly = 0) { priceSnapshotService.hasSnapshots(any(), any()) }
    verify(exactly = 0) { binanceService.getHourlyPrices(any(), any()) }
  }

  @Test
  fun `should cache instrument after DB confirms snapshots exist`() {
    every { snapshotBackfillCacheService.isBackfilled(1L) } returns null
    every { priceSnapshotService.hasSnapshots(1L, instrument.providerName) } returns true
    every { snapshotBackfillCacheService.markBackfilled(1L) } returns true

    service.backfillFromBinance(instrument)

    verify(exactly = 1) { snapshotBackfillCacheService.markBackfilled(1L) }
    verify(exactly = 0) { binanceService.getHourlyPrices(any(), any()) }
  }
}
