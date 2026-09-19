package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.model.InstrumentSnapshot
import ee.tenman.portfolio.repository.DailyPriceRepository
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.calculation.XirrCalculationService
import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import ee.tenman.portfolio.service.instrument.InstrumentSnapshotService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PortfolioXirrWindowServiceTest {
  private val summaryRepository = mockk<PortfolioDailySummaryRepository>()
  private val transactionRepository = mockk<PortfolioTransactionRepository>(relaxed = true)
  private val summaryService = mockk<SummaryService>()
  private val xirrCalculationService = mockk<XirrCalculationService>()
  private val today = LocalDate.of(2026, 5, 6)
  private val clock = Clock.fixed(Instant.parse("2026-05-06T12:00:00Z"), ZoneId.of("UTC"))
  private val service =
    PortfolioXirrWindowService(
      summaryRepository,
      transactionRepository,
      summaryService,
      xirrCalculationService,
      clock,
    )

  @Test
  fun `returns null xirr when no opening summary exists for window`() {
    every { summaryService.getCurrentDaySummary() } returns summary(today, BigDecimal("10000"))
    every { summaryRepository.findFirstByEntryDateLessThanEqualOrderByEntryDateDesc(any()) } returns null

    val result = service.calculate(platforms = null)

    expect(result.windows.map { it.period }).toContainExactly("1M", "3M", "6M", "YTD", "1Y", "2Y", "3Y")
    result.windows.forEach { window ->
      expect(window.xirr).toEqual(null)
      expect(window.fromDate).toEqual(null)
    }
  }

  @Test
  fun `unfiltered call uses stored summary repo and adjusted xirr result`() {
    val openingDate = today.minusMonths(1)
    every { summaryService.getCurrentDaySummary() } returns summary(today, BigDecimal("11000"))
    every { summaryRepository.findFirstByEntryDateLessThanEqualOrderByEntryDateDesc(any()) } returns
      summary(openingDate, BigDecimal("10000"))
    every { transactionRepository.findAllByDateRangeWithInstruments(any(), any()) } returns emptyList()
    val captured = slot<List<CashFlow>>()
    every { xirrCalculationService.calculateAdjustedXirr(capture(captured), today) } returns 0.12

    val result = service.calculate(platforms = null)

    val oneMonthRow = result.windows.first { it.period == "1M" }
    expect(oneMonthRow.fromDate).toEqual(openingDate)
    expect(oneMonthRow.xirr).notToEqualNull()
    expect(captured.captured.first().amount).toEqual(-10000.0)
    expect(captured.captured.last().amount).toEqual(11000.0)
  }

  @Test
  fun `platform-filtered call routes through summaryService and platform-aware tx repo`() {
    val platforms = listOf(Platform.LIGHTYEAR)
    every { summaryService.getSummaryForPlatformsOnDate(platforms, today) } returns summary(today, BigDecimal("5000"))
    every { summaryService.getSummaryForPlatformsOnDate(platforms, any()) } answers {
      summary(secondArg<LocalDate>(), BigDecimal("4000"))
    }
    every { summaryService.getSummaryForPlatformsOnDate(platforms, today) } returns summary(today, BigDecimal("5000"))
    every {
      transactionRepository.findAllByPlatformsAndDateRangeWithInstruments(platforms, any(), any())
    } returns emptyList()
    every { xirrCalculationService.calculateAdjustedXirr(any(), today) } returns 0.07

    val result = service.calculate(platforms = platforms)

    val oneYearRow = result.windows.first { it.period == "1Y" }
    expect(oneYearRow.xirr).notToEqualNull()
    expect(oneYearRow.fromDate).toEqual(today.minusYears(1))
  }

  @ParameterizedTest
  @CsvSource("2024-02-29, 2024-01-01", "2026-09-17, 2026-01-01", "2026-12-31, 2026-01-01", "2027-01-08, 2027-01-01")
  fun `calculates YTD from January first of the current calendar year`(
    date: LocalDate,
    start: LocalDate,
  ) {
    val calendar = Clock.fixed(date.atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"))
    val calculator = PortfolioXirrWindowService(summaryRepository, transactionRepository, summaryService, xirrCalculationService, calendar)
    every { summaryService.getCurrentDaySummary() } returns summary(date, BigDecimal("11000"))
    every { summaryRepository.findFirstByEntryDateLessThanEqualOrderByEntryDateDesc(any()) } returns null
    every { summaryRepository.findFirstByEntryDateLessThanEqualOrderByEntryDateDesc(start) } returns summary(start, BigDecimal("10000"))
    every { xirrCalculationService.calculateAdjustedXirr(any(), date) } returns 0.1234567
    val result = calculator.calculate(platforms = null).windows.firstOrNull { it.period == "YTD" }
    expect(result).notToEqualNull()
    expect(result?.fromDate).toEqual(start)
    expect(result?.xirr).notToEqualNull().toEqualNumerically(BigDecimal("0.123457"))
  }

  @Test
  fun `includes cash flows after the YTD opening snapshot`() {
    val start = LocalDate.of(2026, 1, 1)
    val transaction = mockk<PortfolioTransaction>()
    val deposit = CashFlow(-500.0, LocalDate.of(2026, 2, 10))
    val captured = slot<List<CashFlow>>()
    every { summaryService.getCurrentDaySummary() } returns summary(today, BigDecimal("11000"))
    every { summaryRepository.findFirstByEntryDateLessThanEqualOrderByEntryDateDesc(any()) } returns null
    every { summaryRepository.findFirstByEntryDateLessThanEqualOrderByEntryDateDesc(start) } returns summary(start, BigDecimal("10000"))
    every { transactionRepository.findAllByDateRangeWithInstruments(LocalDate.of(2026, 1, 2), today) } returns listOf(transaction)
    every { xirrCalculationService.convertToCashFlow(transaction) } returns deposit
    every { xirrCalculationService.calculateAdjustedXirr(capture(captured), today) } returns 0.12
    service.calculate(platforms = null)
    expect(captured.isCaptured).toEqual(true)
    expect(captured.captured).toContainExactly(CashFlow(-10000.0, start), deposit, CashFlow(11000.0, today))
  }

  @Test
  fun `calculates YTD using the selected platforms`() {
    val platforms = listOf(Platform.LIGHTYEAR)
    val start = LocalDate.of(2026, 1, 1)
    val transaction = mockk<PortfolioTransaction>()
    val deposit = CashFlow(-250.0, LocalDate.of(2026, 3, 12))
    val captured = slot<List<CashFlow>>()
    every { summaryService.getSummaryForPlatformsOnDate(platforms, any()) } answers { summary(secondArg(), BigDecimal.ZERO) }
    every { summaryService.getSummaryForPlatformsOnDate(platforms, start) } returns summary(start, BigDecimal("4000"))
    every { summaryService.getSummaryForPlatformsOnDate(platforms, today) } returns summary(today, BigDecimal("5000"))
    every {
      transactionRepository.findAllByPlatformsAndDateRangeWithInstruments(platforms, LocalDate.of(2026, 1, 2), today)
    } returns listOf(transaction)
    every { xirrCalculationService.convertToCashFlow(transaction) } returns deposit
    every { xirrCalculationService.calculateAdjustedXirr(capture(captured), today) } returns 0.07
    val result = service.calculate(platforms).windows.firstOrNull { it.period == "YTD" }
    expect(result?.fromDate).toEqual(start)
    expect(result?.xirr).notToEqualNull().toEqualNumerically(BigDecimal("0.07"))
    expect(captured.captured).toContainExactly(CashFlow(-4000.0, start), deposit, CashFlow(5000.0, today))
  }

  @ParameterizedTest
  @CsvSource("2027-01-01, 2027-01-01", "2027-01-01, 2026-12-31", "2027-01-06, 2026-12-31")
  fun `returns unavailable YTD xirr when the opening snapshot is newer than the minimum window`(
    date: LocalDate,
    persisted: LocalDate,
  ) {
    val calendar = Clock.fixed(date.atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"))
    val calculator =
      PortfolioXirrWindowService(summaryRepository, transactionRepository, summaryService, xirrCalculationService, calendar)
    every { summaryService.getCurrentDaySummary() } returns summary(date, BigDecimal("10100"))
    every { summaryRepository.findFirstByEntryDateLessThanEqualOrderByEntryDateDesc(any()) } answers {
      summary(persisted, BigDecimal("10000")).takeIf { !persisted.isAfter(firstArg()) }
    }
    every { xirrCalculationService.calculateAdjustedXirr(any(), date) } returns 0.164271
    val result = calculator.calculate(platforms = null).windows.firstOrNull { it.period == "YTD" }
    expect(result).notToEqualNull()
    expect(result?.fromDate).toEqual(null)
    expect(result?.xirr).toEqual(null)
  }

  private fun summary(
    date: LocalDate,
    totalValue: BigDecimal,
  ): PortfolioDailySummary =
    PortfolioDailySummary(
      entryDate = date,
      totalValue = totalValue,
      xirrAnnualReturn = BigDecimal.ZERO,
      totalProfit = BigDecimal.ZERO,
      earningsPerDay = BigDecimal.ZERO,
    )
}

class PortfolioAnnualWindowServiceTest {
  private val instrumentSnapshotService = mockk<InstrumentSnapshotService>()
  private val dailyPriceRepository = mockk<DailyPriceRepository>()
  private val today = LocalDate.of(2026, 5, 6)
  private val clock = Clock.fixed(Instant.parse("2026-05-06T12:00:00Z"), ZoneId.of("UTC"))
  private val service = PortfolioAnnualWindowService(instrumentSnapshotService, dailyPriceRepository, clock)

  @Test
  fun `returns all-null windows when there are no current holdings`() {
    every { instrumentSnapshotService.getAllSnapshots(null) } returns emptyList()

    val result = service.calculate(platforms = null)

    expect(result.windows.map { it.period }).toContainExactly("1M", "3M", "6M", "YTD", "1Y", "2Y", "3Y")
    result.windows.forEach { window ->
      expect(window.annualReturn).toEqual(null)
      expect(window.fromDate).toEqual(null)
    }
  }

  @Test
  fun `computes buy-and-hold annualized return when full history exists`() {
    val instrument = makeInstrument(currentPrice = BigDecimal("200"))
    val snapshot = makeSnapshot(instrument, quantity = BigDecimal("100"), currentValue = BigDecimal("20000"))
    every { instrumentSnapshotService.getAllSnapshots(null) } returns listOf(snapshot)
    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateLessThanEqualOrderByEntryDateDesc(instrument, any())
    } answers {
      DailyPrice(
        instrument = instrument,
        entryDate = secondArg<LocalDate>(),
        providerName = ProviderName.FT,
        openPrice = null,
        highPrice = null,
        lowPrice = null,
        closePrice = BigDecimal("100"),
        volume = null,
      )
    }

    val result = service.calculate(platforms = null)

    val oneYearRow = result.windows.first { it.period == "1Y" }
    expect(oneYearRow.fromDate).toEqual(today.minusYears(1))
    expect(oneYearRow.annualReturn).notToEqualNull()
    expect(oneYearRow.annualReturn!!.toDouble()).toBeGreaterThan(0.99)
    expect(oneYearRow.annualReturn!!.toDouble()).toBeLessThan(1.01)
  }

  @Test
  fun `returns null for windows that predate earliest available price history`() {
    val instrument = makeInstrument(currentPrice = BigDecimal("110"))
    val snapshot = makeSnapshot(instrument, quantity = BigDecimal("10"), currentValue = BigDecimal("1100"))
    val earliestPriceDate = today.minusMonths(8)
    every { instrumentSnapshotService.getAllSnapshots(null) } returns listOf(snapshot)
    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateLessThanEqualOrderByEntryDateDesc(instrument, any())
    } answers {
      val target = secondArg<LocalDate>()
      if (target.isBefore(earliestPriceDate)) {
        null
      } else {
        DailyPrice(
          instrument = instrument,
          entryDate = if (target.isAfter(earliestPriceDate)) earliestPriceDate else target,
          providerName = ProviderName.FT,
          openPrice = null,
          highPrice = null,
          lowPrice = null,
          closePrice = BigDecimal("100"),
          volume = null,
        )
      }
    }
    every { dailyPriceRepository.findFirstByInstrumentOrderByEntryDateAsc(instrument) } returns
      DailyPrice(
        instrument = instrument,
        entryDate = earliestPriceDate,
        providerName = ProviderName.FT,
        openPrice = null,
        highPrice = null,
        lowPrice = null,
        closePrice = BigDecimal("100"),
        volume = null,
      )

    val result = service.calculate(platforms = null)

    val threeYearRow = result.windows.first { it.period == "3Y" }
    expect(threeYearRow.fromDate).toEqual(null)
    expect(threeYearRow.annualReturn).toEqual(null)
    val sixMonthRow = result.windows.first { it.period == "6M" }
    expect(sixMonthRow.annualReturn).notToEqualNull()
  }

  @Test
  fun `forwards platform filter to instrument snapshot service`() {
    every { instrumentSnapshotService.getAllSnapshots(listOf("LIGHTYEAR")) } returns emptyList()

    service.calculate(platforms = listOf(Platform.LIGHTYEAR))

    verify { instrumentSnapshotService.getAllSnapshots(listOf("LIGHTYEAR")) }
  }

  @ParameterizedTest
  @CsvSource("2024-07-01, 2024-01-01, 0.210792", "2026-07-01, 2026-01-01, 0.212073", "2026-12-31, 2026-01-01, 0.100360")
  fun `calculates YTD from January first using selected platforms`(
    date: LocalDate,
    start: LocalDate,
    expected: BigDecimal,
  ) {
    val calendar = Clock.fixed(date.atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"))
    val calculator = PortfolioAnnualWindowService(instrumentSnapshotService, dailyPriceRepository, calendar)
    val instrument = makeInstrument(BigDecimal("110"))
    val snapshot = makeSnapshot(instrument, BigDecimal("10"), BigDecimal("1100"))
    every { instrumentSnapshotService.getAllSnapshots(listOf("LIGHTYEAR")) } returns listOf(snapshot)
    every { dailyPriceRepository.findFirstByInstrumentAndEntryDateLessThanEqualOrderByEntryDateDesc(instrument, any()) } returns null
    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateLessThanEqualOrderByEntryDateDesc(instrument, start)
    } returns price(instrument, start)
    every { dailyPriceRepository.findFirstByInstrumentOrderByEntryDateAsc(instrument) } returns null
    val result = calculator.calculate(listOf(Platform.LIGHTYEAR)).windows.firstOrNull { it.period == "YTD" }
    expect(result?.fromDate).toEqual(start)
    expect(result?.annualReturn).notToEqualNull().toEqualNumerically(expected)
  }

  @ParameterizedTest
  @CsvSource("2027-01-01, 2027-01-01", "2027-01-07, 2027-01-01", "2026-05-06, 2026-02-01")
  fun `returns unavailable YTD when the elapsed time or price history is insufficient`(
    date: LocalDate,
    opening: LocalDate,
  ) {
    val calendar = Clock.fixed(date.atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"))
    val calculator = PortfolioAnnualWindowService(instrumentSnapshotService, dailyPriceRepository, calendar)
    val instrument = makeInstrument(BigDecimal("110"))
    val snapshot = makeSnapshot(instrument, BigDecimal("10"), BigDecimal("1100"))
    every { instrumentSnapshotService.getAllSnapshots(null) } returns listOf(snapshot)
    every { dailyPriceRepository.findFirstByInstrumentAndEntryDateLessThanEqualOrderByEntryDateDesc(instrument, any()) } answers {
      price(instrument, opening).takeIf { !opening.isAfter(secondArg()) }
    }
    every { dailyPriceRepository.findFirstByInstrumentOrderByEntryDateAsc(instrument) } returns price(instrument, opening)
    val result = calculator.calculate(null).windows.firstOrNull { it.period == "YTD" }
    expect(result).notToEqualNull()
    expect(result?.fromDate).toEqual(null)
    expect(result?.annualReturn).toEqual(null)
  }

  private fun price(
    instrument: Instrument,
    date: LocalDate,
  ): DailyPrice =
    DailyPrice(
      instrument = instrument,
      entryDate = date,
      providerName = ProviderName.FT,
      openPrice = null,
      highPrice = null,
      lowPrice = null,
      closePrice = BigDecimal("100"),
      volume = null,
    )

  private fun makeInstrument(currentPrice: BigDecimal): Instrument =
    Instrument(
      symbol = "TEST",
      name = "Test Instrument",
      category = "ETF",
      baseCurrency = "EUR",
      currentPrice = currentPrice,
      providerName = ProviderName.FT,
    ).apply { id = 1L }

  private fun makeSnapshot(
    instrument: Instrument,
    quantity: BigDecimal,
    currentValue: BigDecimal,
  ): InstrumentSnapshot =
    InstrumentSnapshot(
      instrument = instrument,
      quantity = quantity,
      currentValue = currentValue,
    )
}
