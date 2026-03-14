package ee.tenman.portfolio.service.comparison

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.DailyPriceRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.createInstrument
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class InstrumentComparisonServiceTest {
  private val dailyPriceRepository = mockk<DailyPriceRepository>()
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val clock = Clock.fixed(Instant.parse("2024-06-15T10:00:00Z"), ZoneId.of("UTC"))
  private val service = InstrumentComparisonService(dailyPriceRepository, instrumentRepository, clock)

  private lateinit var instrumentA: Instrument
  private lateinit var instrumentB: Instrument

  @BeforeEach
  fun setUp() {
    clearMocks(dailyPriceRepository, instrumentRepository)
    instrumentA = createInstrument(symbol = "AAPL", name = "Apple Inc.", id = 1L, currentPrice = BigDecimal("200.00"))
    instrumentB = createInstrument(symbol = "GOOGL", name = "Alphabet Inc.", id = 2L, currentPrice = BigDecimal("150.00"))
  }

  @Test
  fun `should normalize prices to percentage change from base`() {
    val ids = listOf(1L)
    val startDate = LocalDate.of(2024, 5, 15)
    val prices =
      listOf(
      createDailyPrice(instrumentA, startDate, BigDecimal("100.00")),
      createDailyPrice(instrumentA, startDate.plusDays(1), BigDecimal("110.00")),
      createDailyPrice(instrumentA, startDate.plusDays(2), BigDecimal("90.00")),
    )
    every { instrumentRepository.findAllById(ids) } returns listOf(instrumentA)
    every { dailyPriceRepository.findAllByInstrumentIdInAndEntryDateBetween(ids, startDate, LocalDate.of(2024, 6, 15)) } returns prices

    val result = service.getComparisonData(ids, "1M")

    expect(result.instruments).toHaveSize(1)
    val dataPoints = result.instruments[0].dataPoints
    expect(dataPoints).toHaveSize(3)
    expect(dataPoints[0].percentageChange).toEqual(0.0)
    expect(dataPoints[1].percentageChange).toEqual(10.0)
    expect(dataPoints[2].percentageChange).toEqual(-10.0)
  }

  @Test
  fun `should align instruments to common start date`() {
    val ids = listOf(1L, 2L)
    val earlyDate = LocalDate.of(2024, 1, 1)
    val commonDate = LocalDate.of(2024, 1, 5)
    val laterDate = LocalDate.of(2024, 1, 10)
    val prices =
      listOf(
      createDailyPrice(instrumentA, earlyDate, BigDecimal("100.00")),
      createDailyPrice(instrumentA, commonDate, BigDecimal("105.00")),
      createDailyPrice(instrumentA, laterDate, BigDecimal("110.00")),
      createDailyPrice(instrumentB, commonDate, BigDecimal("50.00")),
      createDailyPrice(instrumentB, laterDate, BigDecimal("55.00")),
    )
    every { instrumentRepository.findAllById(ids) } returns listOf(instrumentA, instrumentB)
    every { dailyPriceRepository.findAllByInstrumentIdInAndEntryDateBetween(ids, any(), any()) } returns prices

    val result = service.getComparisonData(ids, "1Y")

    expect(result.instruments).toHaveSize(2)
    val aPoints = result.instruments[0].dataPoints
    val bPoints = result.instruments[1].dataPoints
    expect(aPoints[0].date).toEqual(commonDate)
    expect(bPoints[0].date).toEqual(commonDate)
    expect(aPoints[0].percentageChange).toEqual(0.0)
    expect(bPoints[0].percentageChange).toEqual(0.0)
  }

  @Test
  fun `should calculate total change percent correctly`() {
    val ids = listOf(1L)
    val startDate = LocalDate.of(2024, 5, 15)
    val prices =
      listOf(
      createDailyPrice(instrumentA, startDate, BigDecimal("100.00")),
      createDailyPrice(instrumentA, startDate.plusDays(5), BigDecimal("125.00")),
    )
    every { instrumentRepository.findAllById(ids) } returns listOf(instrumentA)
    every { dailyPriceRepository.findAllByInstrumentIdInAndEntryDateBetween(ids, startDate, LocalDate.of(2024, 6, 15)) } returns prices

    val result = service.getComparisonData(ids, "1M")

    expect(result.instruments[0].totalChangePercent).toEqual(25.0)
  }

  @Test
  fun `should resolve period start dates correctly`() {
    val today = LocalDate.of(2024, 6, 15)

    expect(service.resolveStartDate("1M", today)).toEqual(LocalDate.of(2024, 5, 15))
    expect(service.resolveStartDate("6M", today)).toEqual(LocalDate.of(2023, 12, 15))
    expect(service.resolveStartDate("YTD", today)).toEqual(LocalDate.of(2024, 1, 1))
    expect(service.resolveStartDate("1Y", today)).toEqual(LocalDate.of(2023, 6, 15))
    expect(service.resolveStartDate("2Y", today)).toEqual(LocalDate.of(2022, 6, 15))
    expect(service.resolveStartDate("3Y", today)).toEqual(LocalDate.of(2021, 6, 15))
    expect(service.resolveStartDate("4Y", today)).toEqual(LocalDate.of(2020, 6, 15))
    expect(service.resolveStartDate("5Y", today)).toEqual(LocalDate.of(2019, 6, 15))
    expect(service.resolveStartDate("MAX", today)).toEqual(LocalDate.of(2000, 1, 1))
  }

  @Test
  fun `should return empty instruments when no prices found`() {
    val ids = listOf(1L)
    every { instrumentRepository.findAllById(ids) } returns listOf(instrumentA)
    every { dailyPriceRepository.findAllByInstrumentIdInAndEntryDateBetween(ids, any(), any()) } returns emptyList()

    val result = service.getComparisonData(ids, "1Y")

    expect(result.instruments).toBeEmpty()
  }

  @Test
  fun `should sample data points when exceeding maximum`() {
    val ids = listOf(1L)
    val startDate = LocalDate.of(2022, 6, 15)
    val prices =
      (0L..400L).map { day ->
      createDailyPrice(instrumentA, startDate.plusDays(day), BigDecimal(100 + day))
    }
    every { instrumentRepository.findAllById(ids) } returns listOf(instrumentA)
    every { dailyPriceRepository.findAllByInstrumentIdInAndEntryDateBetween(ids, any(), any()) } returns prices

    val result = service.getComparisonData(ids, "5Y")

    expect(result.instruments[0].dataPoints.size).toBeLessThanOrEqualTo(250)
    expect(result.instruments[0].dataPoints.size).toBeGreaterThan(0)
  }

  @Test
  fun `should handle single data point per instrument`() {
    val ids = listOf(1L)
    val date = LocalDate.of(2024, 6, 10)
    val prices = listOf(createDailyPrice(instrumentA, date, BigDecimal("100.00")))
    every { instrumentRepository.findAllById(ids) } returns listOf(instrumentA)
    every { dailyPriceRepository.findAllByInstrumentIdInAndEntryDateBetween(ids, any(), any()) } returns prices

    val result = service.getComparisonData(ids, "1M")

    expect(result.instruments).toHaveSize(1)
    expect(result.instruments[0].dataPoints).toHaveSize(1)
    expect(result.instruments[0].totalChangePercent).toEqual(0.0)
  }

  @Test
  fun `should use default period when unknown period is provided`() {
    val today = LocalDate.of(2024, 6, 15)

    expect(service.resolveStartDate("UNKNOWN", today)).toEqual(LocalDate.of(2023, 6, 15))
  }

  @Test
  fun `should include correct instrument metadata in response`() {
    val ids = listOf(1L)
    val prices =
      listOf(
      createDailyPrice(instrumentA, LocalDate.of(2024, 5, 15), BigDecimal("100.00")),
      createDailyPrice(instrumentA, LocalDate.of(2024, 6, 15), BigDecimal("120.00")),
    )
    every { instrumentRepository.findAllById(ids) } returns listOf(instrumentA)
    every { dailyPriceRepository.findAllByInstrumentIdInAndEntryDateBetween(ids, any(), any()) } returns prices

    val result = service.getComparisonData(ids, "1M")

    val comparison = result.instruments[0]
    expect(comparison.instrumentId).toEqual(1L)
    expect(comparison.symbol).toEqual("AAPL")
    expect(comparison.name).toEqual("Apple Inc.")
    expect(comparison.currentPrice).notToEqualNull().toEqualNumerically(BigDecimal("200.00"))
  }

  private fun createDailyPrice(
    instrument: Instrument,
    date: LocalDate,
    closePrice: BigDecimal,
  ): DailyPrice =
    DailyPrice(
      instrument = instrument,
      entryDate = date,
      providerName = ProviderName.FT,
      openPrice = null,
      highPrice = null,
      lowPrice = null,
      closePrice = closePrice,
      volume = null,
    )
}
