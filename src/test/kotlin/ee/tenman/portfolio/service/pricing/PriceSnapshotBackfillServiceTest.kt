package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.binance.BinanceService
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.PriceSnapshotRepository
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
  private val priceSnapshotRepository = mockk<PriceSnapshotRepository>()
  private val service = PriceSnapshotBackfillService(binanceService, priceSnapshotRepository)

  private lateinit var instrument: Instrument

  @BeforeEach
  fun setUp() {
    clearMocks(binanceService, priceSnapshotRepository)
    instrument =
      Instrument(
        symbol = "BTCEUR",
        name = "Bitcoin EUR",
        category = "Crypto",
        baseCurrency = "EUR",
        currentPrice = BigDecimal("50000.00"),
        providerName = ProviderName.BINANCE,
      ).apply { id = 1L }
  }

  @Test
  fun `should backfill hourly snapshots from Binance API`() {
    val hour1 = Instant.parse("2024-01-15T08:00:00Z")
    val hour2 = Instant.parse("2024-01-15T09:00:00Z")
    val hourlyPrices = TreeMap<Instant, BigDecimal>()
    hourlyPrices[hour1] = BigDecimal("49000.00")
    hourlyPrices[hour2] = BigDecimal("50000.00")
    every { binanceService.getHourlyPrices("BTCEUR", 48) } returns hourlyPrices
    every { priceSnapshotRepository.upsert(any(), any(), any(), any()) } just runs

    service.backfillFromBinance(instrument)

    verify(exactly = 1) { priceSnapshotRepository.upsert(1L, "BINANCE", hour1, BigDecimal("49000.00")) }
    verify(exactly = 1) { priceSnapshotRepository.upsert(1L, "BINANCE", hour2, BigDecimal("50000.00")) }
  }

  @Test
  fun `should handle empty hourly prices from Binance`() {
    every { binanceService.getHourlyPrices("BTCEUR", 48) } returns TreeMap()

    service.backfillFromBinance(instrument)

    verify(exactly = 0) { priceSnapshotRepository.upsert(any(), any(), any(), any()) }
  }
}
