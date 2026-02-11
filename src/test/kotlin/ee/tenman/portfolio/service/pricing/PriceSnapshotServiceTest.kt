package ee.tenman.portfolio.service.pricing

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PriceSnapshot
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.PriceSnapshotRepository
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.TreeMap

class PriceSnapshotServiceTest {
  private val priceSnapshotRepository = mockk<PriceSnapshotRepository>()
  private val clock = Clock.fixed(Instant.parse("2024-01-15T14:35:00Z"), ZoneId.of("UTC"))
  private val deletionService = mockk<PriceSnapshotDeletionService>()
  private val service = PriceSnapshotService(priceSnapshotRepository, clock, deletionService)

  private lateinit var instrument: Instrument
  private val hour = Instant.parse("2024-01-15T14:00:00Z")

  @BeforeEach
  fun setUp() {
    clearMocks(priceSnapshotRepository, deletionService)
    instrument =
      createInstrument(
        symbol = "BTCEUR",
        name = "Bitcoin EUR",
        category = "Crypto",
        baseCurrency = "EUR",
        currentPrice = BigDecimal("45000.00"),
        providerName = ProviderName.BINANCE,
      )
  }

  @Test
  fun `should upsert snapshot truncated to current hour`() {
    every { priceSnapshotRepository.upsert(1L, "BINANCE", hour, BigDecimal("45000.00")) } just runs

    service.saveSnapshot(instrument, BigDecimal("45000.00"), ProviderName.BINANCE)

    verify { priceSnapshotRepository.upsert(1L, "BINANCE", hour, BigDecimal("45000.00")) }
  }

  @Test
  fun `should not call upsert when hourly prices map is empty`() {
    service.saveSnapshots(1L, ProviderName.BINANCE, TreeMap())

    verify(exactly = 0) { priceSnapshotRepository.upsert(any(), any(), any(), any()) }
  }

  @Test
  fun `should save each hourly price via individual upsert`() {
    val hour1 = Instant.parse("2024-01-15T08:00:00Z")
    val hour2 = Instant.parse("2024-01-15T09:00:00Z")
    val hourlyPrices = TreeMap<Instant, BigDecimal>()
    hourlyPrices[hour1] = BigDecimal("49000.00")
    hourlyPrices[hour2] = BigDecimal("50000.00")
    every { priceSnapshotRepository.upsert(any(), any(), any(), any()) } just runs

    service.saveSnapshots(1L, ProviderName.BINANCE, hourlyPrices)

    verify(exactly = 1) { priceSnapshotRepository.upsert(1L, "BINANCE", hour1, BigDecimal("49000.00")) }
    verify(exactly = 1) { priceSnapshotRepository.upsert(1L, "BINANCE", hour2, BigDecimal("50000.00")) }
  }

  @Test
  fun `should return true when snapshots exist for instrument`() {
    every { priceSnapshotRepository.existsByInstrumentIdAndProviderName(1L, "BINANCE") } returns true

    expect(service.hasSnapshots(1L, ProviderName.BINANCE)).toEqual(true)
  }

  @Test
  fun `should return false when no snapshots exist for instrument`() {
    every { priceSnapshotRepository.existsByInstrumentIdAndProviderName(1L, "BINANCE") } returns false

    expect(service.hasSnapshots(1L, ProviderName.BINANCE)).toEqual(false)
  }

  @Test
  fun `should find closest snapshot before target hour`() {
    val snapshot = createSnapshot()
    val earliestHour = Instant.parse("2024-01-15T08:00:00Z")
    every { priceSnapshotRepository.findClosestAtOrBefore(1L, ProviderName.BINANCE, earliestHour, hour) } returns snapshot

    val result = service.findClosestAtOrBefore(1L, ProviderName.BINANCE, earliestHour, hour)

    expect(result).toEqual(snapshot)
  }

  @Test
  fun `should return null when no snapshot found before target hour`() {
    val earliestHour = Instant.parse("2024-01-15T08:00:00Z")
    every { priceSnapshotRepository.findClosestAtOrBefore(1L, ProviderName.BINANCE, earliestHour, hour) } returns null

    val result = service.findClosestAtOrBefore(1L, ProviderName.BINANCE, earliestHour, hour)

    expect(result).toEqual(null)
  }

  @Test
  fun `should delete snapshots in batches until none remain`() {
    val cutoff = Instant.parse("2023-12-16T00:00:00Z")
    every { deletionService.deleteBatch(cutoff) } returns 1000 andThen 500

    service.deleteOlderThan(cutoff)

    verify(exactly = 2) { deletionService.deleteBatch(cutoff) }
  }

  @Test
  fun `should stop deleting when batch returns less than batch size`() {
    val cutoff = Instant.parse("2023-12-16T00:00:00Z")
    every { deletionService.deleteBatch(cutoff) } returns 0

    service.deleteOlderThan(cutoff)

    verify(exactly = 1) { deletionService.deleteBatch(cutoff) }
  }

  private fun createSnapshot(
    price: BigDecimal = BigDecimal("45000.00"),
    hour: Instant = Instant.parse("2024-01-15T14:00:00Z"),
  ): PriceSnapshot =
    PriceSnapshot(
      instrument = instrument,
      providerName = ProviderName.BINANCE,
      snapshotHour = hour,
      price = price,
    )
}
