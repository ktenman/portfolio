package ee.tenman.portfolio.service.pricing

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PriceSnapshot
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class PriceSnapshotServiceTest {
  private val priceSnapshotRepository = mockk<PriceSnapshotRepository>()
  private val fixedInstant = Instant.parse("2024-01-15T14:35:00Z")
  private val clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))
  private val service = PriceSnapshotService(priceSnapshotRepository, clock)

  private lateinit var instrument: Instrument
  private val truncatedHour = fixedInstant.truncatedTo(ChronoUnit.HOURS)

  @BeforeEach
  fun setUp() {
    clearMocks(priceSnapshotRepository)
    instrument =
      Instrument(
      symbol = "BTCEUR",
      name = "Bitcoin EUR",
      category = "Crypto",
      baseCurrency = "EUR",
      currentPrice = BigDecimal("45000.00"),
      providerName = ProviderName.BINANCE,
    ).apply { id = 1L }
  }

  @Test
  fun `should save snapshot when no previous snapshot exists`() {
    every { priceSnapshotRepository.findLatestByInstrumentAndProvider(1L, "BINANCE") } returns null
    every { priceSnapshotRepository.upsert(1L, "BINANCE", truncatedHour, BigDecimal("45000.00")) } just runs

    service.saveSnapshot(instrument, BigDecimal("45000.00"), ProviderName.BINANCE)

    verify { priceSnapshotRepository.upsert(1L, "BINANCE", truncatedHour, BigDecimal("45000.00")) }
  }

  @Test
  fun `should save snapshot when price differs from latest`() {
    val latest =
      PriceSnapshot(
      instrument = instrument,
      providerName = ProviderName.BINANCE,
      snapshotHour = truncatedHour.minus(1, ChronoUnit.HOURS),
      price = BigDecimal("44000.00"),
    )
    every { priceSnapshotRepository.findLatestByInstrumentAndProvider(1L, "BINANCE") } returns latest
    every { priceSnapshotRepository.upsert(1L, "BINANCE", truncatedHour, BigDecimal("45000.00")) } just runs

    service.saveSnapshot(instrument, BigDecimal("45000.00"), ProviderName.BINANCE)

    verify { priceSnapshotRepository.upsert(1L, "BINANCE", truncatedHour, BigDecimal("45000.00")) }
  }

  @Test
  fun `should skip snapshot when price equals latest`() {
    val latest =
      PriceSnapshot(
      instrument = instrument,
      providerName = ProviderName.BINANCE,
      snapshotHour = truncatedHour.minus(1, ChronoUnit.HOURS),
      price = BigDecimal("45000.00"),
    )
    every { priceSnapshotRepository.findLatestByInstrumentAndProvider(1L, "BINANCE") } returns latest

    service.saveSnapshot(instrument, BigDecimal("45000.00"), ProviderName.BINANCE)

    verify(exactly = 0) { priceSnapshotRepository.upsert(any(), any(), any(), any()) }
  }

  @Test
  fun `should skip snapshot when price equals latest with different scale`() {
    val latest =
      PriceSnapshot(
      instrument = instrument,
      providerName = ProviderName.BINANCE,
      snapshotHour = truncatedHour.minus(1, ChronoUnit.HOURS),
      price = BigDecimal("45000.0000000000"),
    )
    every { priceSnapshotRepository.findLatestByInstrumentAndProvider(1L, "BINANCE") } returns latest

    service.saveSnapshot(instrument, BigDecimal("45000.00"), ProviderName.BINANCE)

    verify(exactly = 0) { priceSnapshotRepository.upsert(any(), any(), any(), any()) }
  }

  @Test
  fun `should truncate snapshot time to hour boundary`() {
    every { priceSnapshotRepository.findLatestByInstrumentAndProvider(1L, "BINANCE") } returns null
    every { priceSnapshotRepository.upsert(1L, "BINANCE", truncatedHour, BigDecimal("100.00")) } just runs

    service.saveSnapshot(instrument, BigDecimal("100.00"), ProviderName.BINANCE)

    verify { priceSnapshotRepository.upsert(1L, "BINANCE", Instant.parse("2024-01-15T14:00:00Z"), BigDecimal("100.00")) }
  }

  @Test
  fun `should find closest snapshot before target hour`() {
    val snapshot =
      PriceSnapshot(
      instrument = instrument,
      providerName = ProviderName.BINANCE,
      snapshotHour = truncatedHour,
      price = BigDecimal("45000.00"),
    )
    every { priceSnapshotRepository.findClosestBefore(1L, truncatedHour) } returns snapshot

    val result = service.findClosestBefore(1L, truncatedHour)

    expect(result).toEqual(snapshot)
  }

  @Test
  fun `should return null when no snapshot found before target hour`() {
    every { priceSnapshotRepository.findClosestBefore(1L, truncatedHour) } returns null

    val result = service.findClosestBefore(1L, truncatedHour)

    expect(result).toEqual(null)
  }

  @Test
  fun `should delete snapshots older than cutoff`() {
    val cutoff = Instant.parse("2023-12-16T00:00:00Z")
    every { priceSnapshotRepository.deleteOlderThan(cutoff) } just runs

    service.deleteOlderThan(cutoff)

    verify { priceSnapshotRepository.deleteOlderThan(cutoff) }
  }
}
