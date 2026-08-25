package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.BenchmarkIndex
import ee.tenman.portfolio.domain.DailyPricePoint
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.TimeRange
import ee.tenman.portfolio.repository.DailyPriceRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional

class BenchmarkSeriesServiceTest {
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val dailyPriceRepository = mockk<DailyPriceRepository>()
  private val today = LocalDate.of(2026, 8, 24)
  private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
  private val service = BenchmarkSeriesService(instrumentRepository, dailyPriceRepository, clock)

  private fun instrument(symbol: String = "VUAA:GER:EUR"): Instrument = Instrument(symbol, "Vanguard S&P 500", "ETF", "EUR")

  private fun point(
    date: LocalDate,
    close: String,
  ): DailyPricePoint = DailyPricePoint(1, date, BigDecimal(close))

  private fun tracked(
    instrument: Instrument,
    vararg points: DailyPricePoint,
  ) {
    every { instrumentRepository.findBySymbol(instrument.symbol) } returns Optional.of(instrument)
    every { dailyPriceRepository.findPricePointsByInstrumentAndEntryDateGreaterThanEqual(instrument, any()) } returns
      points.toList()
  }

  @Test
  fun `should return an empty series when no benchmark instrument exists`() {
    every { instrumentRepository.findBySymbol("VUAA:GER:EUR") } returns Optional.empty()
    every { instrumentRepository.findBySymbol("SPYL:GER:EUR") } returns Optional.empty()
    val result = service.getSeries(TimeRange.ONE_MONTH, BenchmarkIndex.SP500)
    expect(result).toBeEmpty()
  }

  @Test
  fun `should fall back to the spyl instrument when vuaa is missing`() {
    val spyl = instrument("SPYL:GER:EUR")
    every { instrumentRepository.findBySymbol("VUAA:GER:EUR") } returns Optional.empty()
    tracked(spyl, point(today, "12.34"))
    val result = service.getSeries(TimeRange.ONE_MONTH, BenchmarkIndex.SP500)
    expect(result).toHaveSize(1)
  }

  @Test
  fun `should ask the repository for prices from the range start onwards`() {
    val vuaa = instrument()
    val start = slot<LocalDate>()
    every { instrumentRepository.findBySymbol("VUAA:GER:EUR") } returns Optional.of(vuaa)
    every { dailyPriceRepository.findPricePointsByInstrumentAndEntryDateGreaterThanEqual(vuaa, capture(start)) } returns
      listOf(point(today.minusDays(3), "101.00"))
    service.getSeries(TimeRange.ONE_MONTH, BenchmarkIndex.SP500)
    expect(start.captured).toEqual(today.minusMonths(1))
  }

  @Test
  fun `should ask the repository for the whole history when the range is max`() {
    val vuaa = instrument()
    val start = slot<LocalDate>()
    every { instrumentRepository.findBySymbol("VUAA:GER:EUR") } returns Optional.of(vuaa)
    every { dailyPriceRepository.findPricePointsByInstrumentAndEntryDateGreaterThanEqual(vuaa, capture(start)) } returns
      listOf(point(today.minusDays(3), "101.00"))
    service.getSeries(TimeRange.MAX, BenchmarkIndex.SP500)
    expect(start.captured).toEqual(LocalDate.EPOCH)
  }

  @Test
  fun `should keep the first point per date when providers overlap`() {
    val vuaa = instrument()
    tracked(
      vuaa,
      point(today.minusDays(1), "101.50"),
      point(today.minusDays(1), "102.00"),
    )
    val result = service.getSeries(TimeRange.ONE_MONTH, BenchmarkIndex.SP500)
    expect(result.map { it.price.toPlainString() }).toEqual(listOf("101.50"))
  }

  @Test
  fun `should preserve the repository date order for the max range`() {
    val vuaa = instrument()
    tracked(
      vuaa,
      point(today.minusYears(6), "55.00"),
      point(today.minusDays(10), "99.00"),
      point(today.minusDays(1), "102.00"),
    )
    val result = service.getSeries(TimeRange.MAX, BenchmarkIndex.SP500)
    expect(result.map { it.date }).toEqual(
      listOf(today.minusYears(6), today.minusDays(10), today.minusDays(1)),
    )
  }

  @Test
  fun `should map the entry date and close price into the point`() {
    val vuaa = instrument()
    tracked(vuaa, point(today.minusDays(2), "104.56"))
    val result = service.getSeries(TimeRange.ONE_MONTH, BenchmarkIndex.SP500)
    expect(result.first().price).toEqualNumerically(BigDecimal("104.56"))
  }

  @Test
  fun `should resolve the world index to the vwce instrument`() {
    val vwce = instrument("VWCE:GER:EUR")
    tracked(vwce, point(today.minusDays(2), "133.70"))
    val result = service.getSeries(TimeRange.ONE_MONTH, BenchmarkIndex.WORLD)
    expect(result.first().price).toEqualNumerically(BigDecimal("133.70"))
  }

  @Test
  fun `should fall back to the sppw instrument when vwce is missing`() {
    val sppw = instrument("SPPW:GER:EUR")
    every { instrumentRepository.findBySymbol("VWCE:GER:EUR") } returns Optional.empty()
    tracked(sppw, point(today, "35.10"))
    val result = service.getSeries(TimeRange.ONE_MONTH, BenchmarkIndex.WORLD)
    expect(result).toHaveSize(1)
  }
}
