package ee.tenman.portfolio.service.pricing

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.DailyPricePoint
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PriceSnapshot
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TimeRange
import ee.tenman.portfolio.model.PriceChange
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
import java.time.ZoneOffset

class PriceChangeServiceTest {
  private val dailyPriceService = mockk<DailyPriceService>()
  private val priceSnapshotService = mockk<PriceSnapshotService>()
  private val clock = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneOffset.UTC)
  private val priceChangeService = PriceChangeService(dailyPriceService, priceSnapshotService, clock)
  private lateinit var testInstrument: Instrument
  private val testDate = LocalDate.of(2024, 1, 15)

  @BeforeEach
  fun setUp() {
    clearMocks(dailyPriceService, priceSnapshotService)
    testInstrument = createInstrument()
  }

  @Test
  fun `should fall back to daily prices for one day when no snapshots exist`() {
    stubSnapshots()
    stubPreviousPrice(BigDecimal("100.00"), testDate.minusDays(1))
    val result = priceChanges(TimeRange.ONE_DAY, BigDecimal("110.00"))[testInstrument.id]
    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("10.00"))
    expect(result.changePercent).toEqual(10.0)
  }

  @Test
  fun `should measure one day from snapshots when both are fresh`() {
    stubSnapshots(
      createSnapshot(BigDecimal("110.00"), Instant.parse("2024-01-15T09:00:00Z")),
      createSnapshot(BigDecimal("100.00"), Instant.parse("2024-01-14T09:00:00Z")),
    )
    val result = priceChanges(TimeRange.ONE_DAY)[testInstrument.id]
    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("10.00"))
    expect(result.changePercent).toEqual(10.0)
    verify(exactly = 0) { dailyPriceService.findLatestPrices(any(), any(), any()) }
  }

  @Test
  fun `should take the latest snapshot within six hours of each end`() {
    stubSnapshots(
      createSnapshot(BigDecimal("105.00"), Instant.parse("2024-01-15T05:00:00Z")),
      createSnapshot(BigDecimal("120.00"), Instant.parse("2024-01-15T10:00:00Z")),
      createSnapshot(BigDecimal("90.00"), Instant.parse("2024-01-14T04:00:00Z")),
      createSnapshot(BigDecimal("100.00"), Instant.parse("2024-01-14T10:00:00Z")),
    )
    val result = priceChanges(TimeRange.ONE_DAY)[testInstrument.id]
    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("20.00"))
  }

  @Test
  fun `should fall back to daily prices when the current snapshot is stale`() {
    stubSnapshots(
      createSnapshot(BigDecimal("130.00"), Instant.parse("2024-01-15T03:00:00Z")),
      createSnapshot(BigDecimal("100.00"), Instant.parse("2024-01-14T09:00:00Z")),
    )
    stubPreviousPrice(BigDecimal("100.00"), testDate.minusDays(1))
    val result = priceChanges(TimeRange.ONE_DAY, BigDecimal("110.00"))[testInstrument.id]
    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("10.00"))
  }

  @Test
  fun `should fall back to daily prices when the previous snapshot is missing`() {
    stubSnapshots(createSnapshot(BigDecimal("130.00"), Instant.parse("2024-01-15T09:00:00Z")))
    stubPreviousPrice(BigDecimal("100.00"), testDate.minusDays(1))
    val result = priceChanges(TimeRange.ONE_DAY, BigDecimal("110.00"))[testInstrument.id]
    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("10.00"))
  }

  @Test
  fun `should ignore snapshots from another provider`() {
    stubSnapshots(
      createSnapshot(BigDecimal("130.00"), Instant.parse("2024-01-15T09:00:00Z"), ProviderName.BINANCE),
      createSnapshot(BigDecimal("100.00"), Instant.parse("2024-01-14T09:00:00Z"), ProviderName.BINANCE),
    )
    stubPreviousPrice(BigDecimal("100.00"), testDate.minusDays(1))
    val result = priceChanges(TimeRange.ONE_DAY, BigDecimal("110.00"))[testInstrument.id]
    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("10.00"))
  }

  @Test
  fun `should read daily prices only for instruments without fresh snapshots`() {
    val other = createInstrument(symbol = "ÄRI", id = 2L)
    every {
      priceSnapshotService.findAllBetween(listOf(1L, 2L), Instant.parse("2024-01-14T04:00:00Z"), Instant.parse("2024-01-15T10:00:00Z"))
    } returns
      listOf(
        createSnapshot(BigDecimal("110.00"), Instant.parse("2024-01-15T09:00:00Z")),
        createSnapshot(BigDecimal("100.00"), Instant.parse("2024-01-14T09:00:00Z")),
      )
    every {
      dailyPriceService.findLatestPrices(listOf(other), testDate.minusDays(1), testDate.minusDays(6))
    } returns mapOf(2L to BigDecimal("50.00"))
    val lookup = PriceLookup(listOf(DailyPricePoint(2L, testDate, BigDecimal("60.00"))))
    val result = priceChangeService.getPriceChanges(listOf(testInstrument, other), TimeRange.ONE_DAY, lookup)
    expect(result.mapValues { it.value.changePercent }).toEqual(mapOf(1L to 10.0, 2L to 20.0))
  }

  @Test
  fun `should measure one week from daily prices without reading snapshots`() {
    stubPreviousPrice(BigDecimal("100.00"), testDate.minusDays(7))
    val result = priceChanges(TimeRange.ONE_WEEK, BigDecimal("120.00"))[testInstrument.id]
    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("20.00"))
    expect(result.changePercent).toEqual(20.0)
    verify(exactly = 0) { priceSnapshotService.findAllBetween(any(), any(), any()) }
  }

  @Test
  fun `should measure one month from the price a month ago`() {
    stubPreviousPrice(BigDecimal("100.00"), testDate.minusMonths(1))
    val result = priceChanges(TimeRange.ONE_MONTH, BigDecimal("150.00"))[testInstrument.id]
    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("50.00"))
    expect(result.changePercent).toEqual(50.0)
  }

  @Test
  fun `should measure year to date from the first day of the year`() {
    stubPreviousPrice(BigDecimal("100.00"), LocalDate.of(2024, 1, 1))
    val result = priceChanges(TimeRange.YTD, BigDecimal("125.00"))[testInstrument.id]
    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("25.00"))
    expect(result.changePercent).toEqual(25.0)
  }

  @Test
  fun `should return no changes for the max period which has no fixed start`() {
    val result = priceChanges(TimeRange.MAX)
    expect(result).toEqual(emptyMap())
  }

  @Test
  fun `should leave out an instrument without a current price`() {
    stubSnapshots()
    stubPreviousPrice(BigDecimal("100.00"), testDate.minusDays(1))
    val result = priceChanges(TimeRange.ONE_DAY)
    expect(result).toEqual(emptyMap())
  }

  @Test
  fun `should leave out an instrument without a historical price`() {
    stubSnapshots()
    stubPreviousPrice(null, testDate.minusDays(1))
    val result = priceChanges(TimeRange.ONE_DAY, BigDecimal("110.00"))
    expect(result).toEqual(emptyMap())
  }

  @Test
  fun `should return a negative percentage when the price fell`() {
    stubSnapshots()
    stubPreviousPrice(BigDecimal("100.00"), testDate.minusDays(1))
    val result = priceChanges(TimeRange.ONE_DAY, BigDecimal("80.00"))[testInstrument.id]
    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("-20.00"))
    expect(result.changePercent).toEqual(-20.0)
  }

  @Test
  fun `should return zero percent when the previous price is zero`() {
    stubSnapshots()
    stubPreviousPrice(BigDecimal.ZERO, testDate.minusDays(1))
    val result = priceChanges(TimeRange.ONE_DAY, BigDecimal("100.00"))[testInstrument.id]
    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("100.00"))
    expect(result.changePercent).toEqual(0.0)
  }

  private fun stubSnapshots(vararg snapshots: PriceSnapshot) {
    every {
      priceSnapshotService.findAllBetween(listOf(1L), Instant.parse("2024-01-14T04:00:00Z"), Instant.parse("2024-01-15T10:00:00Z"))
    } returns snapshots.toList()
  }

  private fun priceChanges(
    period: TimeRange,
    current: BigDecimal? = null,
  ): Map<Long, PriceChange> {
    val prices = listOfNotNull(current?.let { DailyPricePoint(testInstrument.id, testDate, it) })
    return priceChangeService.getPriceChanges(listOf(testInstrument), period, PriceLookup(prices))
  }

  private fun stubPreviousPrice(
    previous: BigDecimal?,
    targetDate: LocalDate,
  ) {
    every { dailyPriceService.findLatestPrices(listOf(testInstrument), targetDate, targetDate.minusDays(5)) } returns prices(previous)
  }

  private fun prices(price: BigDecimal?): Map<Long, BigDecimal> = if (price == null) emptyMap() else mapOf(testInstrument.id to price)

  private fun createSnapshot(
    price: BigDecimal,
    hour: Instant,
    providerName: ProviderName = ProviderName.FT,
  ): PriceSnapshot =
    PriceSnapshot(
      instrument = testInstrument,
      providerName = providerName,
      snapshotHour = hour,
      price = price,
    )
}
