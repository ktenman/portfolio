package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
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

    expect(result.windows).toHaveSize(6)
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

    expect(result.windows).toHaveSize(6)
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
