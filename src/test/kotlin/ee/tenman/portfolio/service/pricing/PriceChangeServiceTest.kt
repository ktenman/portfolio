package ee.tenman.portfolio.service.pricing

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PriceChangePeriod
import ee.tenman.portfolio.domain.PriceSnapshot
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.createInstrument
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PriceChangeServiceTest {
  private val dailyPriceService = mockk<DailyPriceService>()
  private val priceSnapshotService = mockk<PriceSnapshotService>()
  private val clock = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneId.of("UTC"))
  private val priceChangeService = PriceChangeService(dailyPriceService, priceSnapshotService, clock)

  private lateinit var testInstrument: Instrument
  private val testDate = LocalDate.of(2024, 1, 15)

  @BeforeEach
  fun setUp() {
    clearMocks(dailyPriceService, priceSnapshotService)
    testInstrument = createInstrument()
  }

  @Test
  fun `should getPriceChange with 24h period falls back to daily prices when no snapshots`() {
    every { priceSnapshotService.findClosestAtOrBefore(1L, ProviderName.FT, any(), any()) } returns null
    val currentPrice = createDailyPrice(closePrice = BigDecimal("110.00"), date = testDate)
    val yesterdayPrice = createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1))
    every { dailyPriceService.findLastDailyPrice(testInstrument, testDate) } returns currentPrice
    every { dailyPriceService.findPriceNear(testInstrument, testDate.minusDays(1)) } returns yesterdayPrice

    val result = priceChangeService.getPriceChange(testInstrument, PriceChangePeriod.P24H)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("10.00"))
    expect(result.changePercent).toEqual(10.0)
  }

  @Test
  fun `should getPriceChange with 24h period uses snapshots when available`() {
    val currentSnapshot = createSnapshot(price = BigDecimal("110.00"), hour = Instant.parse("2024-01-15T09:00:00Z"))
    val previousSnapshot = createSnapshot(price = BigDecimal("100.00"), hour = Instant.parse("2024-01-14T09:00:00Z"))
    every {
      priceSnapshotService.findClosestAtOrBefore(
        1L,
        ProviderName.FT,
        Instant.parse("2024-01-15T04:00:00Z"),
        Instant.parse("2024-01-15T10:00:00Z"),
      )
    } returns currentSnapshot
    every {
      priceSnapshotService.findClosestAtOrBefore(
        1L,
        ProviderName.FT,
        Instant.parse("2024-01-14T04:00:00Z"),
        Instant.parse("2024-01-14T10:00:00Z"),
      )
    } returns previousSnapshot

    val result = priceChangeService.getPriceChange(testInstrument, PriceChangePeriod.P24H)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("10.00"))
    expect(result.changePercent).toEqual(10.0)
    verify(exactly = 0) { dailyPriceService.findLastDailyPrice(any(), any()) }
  }

  @Test
  fun `should getPriceChange with 24h period falls back when current snapshot is stale`() {
    every {
      priceSnapshotService.findClosestAtOrBefore(
        1L,
        ProviderName.FT,
        Instant.parse("2024-01-15T04:00:00Z"),
        Instant.parse("2024-01-15T10:00:00Z"),
      )
    } returns null
    val currentPrice = createDailyPrice(closePrice = BigDecimal("110.00"), date = testDate)
    val yesterdayPrice = createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1))
    every { dailyPriceService.findLastDailyPrice(testInstrument, testDate) } returns currentPrice
    every { dailyPriceService.findPriceNear(testInstrument, testDate.minusDays(1)) } returns yesterdayPrice

    val result = priceChangeService.getPriceChange(testInstrument, PriceChangePeriod.P24H)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("10.00"))
    expect(result.changePercent).toEqual(10.0)
  }

  @Test
  fun `should getPriceChange with 24h period falls back when previous snapshot missing`() {
    val currentSnapshot = createSnapshot(price = BigDecimal("110.00"), hour = Instant.parse("2024-01-15T09:00:00Z"))
    every {
      priceSnapshotService.findClosestAtOrBefore(
        1L,
        ProviderName.FT,
        Instant.parse("2024-01-15T04:00:00Z"),
        Instant.parse("2024-01-15T10:00:00Z"),
      )
    } returns currentSnapshot
    every {
      priceSnapshotService.findClosestAtOrBefore(
        1L,
        ProviderName.FT,
        Instant.parse("2024-01-14T04:00:00Z"),
        Instant.parse("2024-01-14T10:00:00Z"),
      )
    } returns null
    val currentPrice = createDailyPrice(closePrice = BigDecimal("110.00"), date = testDate)
    val yesterdayPrice = createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1))
    every { dailyPriceService.findLastDailyPrice(testInstrument, testDate) } returns currentPrice
    every { dailyPriceService.findPriceNear(testInstrument, testDate.minusDays(1)) } returns yesterdayPrice

    val result = priceChangeService.getPriceChange(testInstrument, PriceChangePeriod.P24H)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("10.00"))
    expect(result.changePercent).toEqual(10.0)
  }

  @Test
  fun `should getPriceChange with 7d period returns correct change`() {
    val currentPrice = createDailyPrice(closePrice = BigDecimal("120.00"), date = testDate)
    val weekAgoPrice = createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(7))
    every { dailyPriceService.findLastDailyPrice(testInstrument, testDate) } returns currentPrice
    every { dailyPriceService.findPriceNear(testInstrument, testDate.minusDays(7)) } returns weekAgoPrice

    val result = priceChangeService.getPriceChange(testInstrument, PriceChangePeriod.P7D)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("20.00"))
    expect(result.changePercent).toEqual(20.0)
    verify(exactly = 0) { priceSnapshotService.findClosestAtOrBefore(any(), any(), any(), any()) }
  }

  @Test
  fun `should getPriceChange with 30d period returns correct change`() {
    val currentPrice = createDailyPrice(closePrice = BigDecimal("150.00"), date = testDate)
    val monthAgoPrice = createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(30))
    every { dailyPriceService.findLastDailyPrice(testInstrument, testDate) } returns currentPrice
    every { dailyPriceService.findPriceNear(testInstrument, testDate.minusDays(30)) } returns monthAgoPrice

    val result = priceChangeService.getPriceChange(testInstrument, PriceChangePeriod.P30D)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("50.00"))
    expect(result.changePercent).toEqual(50.0)
  }

  @Test
  fun `should getPriceChange returns null when current price not found`() {
    every { priceSnapshotService.findClosestAtOrBefore(1L, ProviderName.FT, any(), any()) } returns null
    every { dailyPriceService.findLastDailyPrice(testInstrument, testDate) } returns null

    val result = priceChangeService.getPriceChange(testInstrument, PriceChangePeriod.P24H)

    expect(result).toEqual(null)
  }

  @Test
  fun `should getPriceChange returns null when historical price not found`() {
    every { priceSnapshotService.findClosestAtOrBefore(1L, ProviderName.FT, any(), any()) } returns null
    val currentPrice = createDailyPrice(closePrice = BigDecimal("110.00"), date = testDate)
    every { dailyPriceService.findLastDailyPrice(testInstrument, testDate) } returns currentPrice
    every { dailyPriceService.findPriceNear(testInstrument, testDate.minusDays(1)) } returns null

    val result = priceChangeService.getPriceChange(testInstrument, PriceChangePeriod.P24H)

    expect(result).toEqual(null)
  }

  @Test
  fun `should getPriceChange with negative change returns correct percentage`() {
    every { priceSnapshotService.findClosestAtOrBefore(1L, ProviderName.FT, any(), any()) } returns null
    val currentPrice = createDailyPrice(closePrice = BigDecimal("80.00"), date = testDate)
    val yesterdayPrice = createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1))
    every { dailyPriceService.findLastDailyPrice(testInstrument, testDate) } returns currentPrice
    every { dailyPriceService.findPriceNear(testInstrument, testDate.minusDays(1)) } returns yesterdayPrice

    val result = priceChangeService.getPriceChange(testInstrument, PriceChangePeriod.P24H)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("-20.00"))
    expect(result.changePercent).toEqual(-20.0)
  }

  @Test
  fun `should getPriceChange returns zero percent when previous price is zero`() {
    every { priceSnapshotService.findClosestAtOrBefore(1L, ProviderName.FT, any(), any()) } returns null
    val currentPrice = createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate)
    val zeroPreviousPrice = createDailyPrice(closePrice = BigDecimal.ZERO, date = testDate.minusDays(1))
    every { dailyPriceService.findLastDailyPrice(testInstrument, testDate) } returns currentPrice
    every { dailyPriceService.findPriceNear(testInstrument, testDate.minusDays(1)) } returns zeroPreviousPrice

    val result = priceChangeService.getPriceChange(testInstrument, PriceChangePeriod.P24H)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("100.00"))
    expect(result.changePercent).toEqual(0.0)
  }

  private fun createDailyPrice(
    closePrice: BigDecimal,
    date: LocalDate = testDate,
  ): DailyPrice =
    DailyPrice(
      instrument = testInstrument,
      entryDate = date,
      providerName = ProviderName.FT,
      openPrice = null,
      highPrice = null,
      lowPrice = null,
      closePrice = closePrice,
      volume = null,
    )

  private fun createSnapshot(
    price: BigDecimal,
    hour: Instant,
  ): PriceSnapshot =
    PriceSnapshot(
      instrument = testInstrument,
      providerName = ProviderName.FT,
      snapshotHour = hour,
      price = price,
    )
}
