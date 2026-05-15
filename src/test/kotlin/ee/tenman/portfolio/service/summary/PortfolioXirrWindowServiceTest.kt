package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.calculation.XirrCalculationService
import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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

    expect(result.windows).toHaveSize(7)
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
  fun `ytd window starts on january first of current year`() {
    val ytdStart = LocalDate.of(today.year, 1, 1)
    every { summaryService.getCurrentDaySummary() } returns summary(today, BigDecimal("12000"))
    every { summaryRepository.findFirstByEntryDateLessThanEqualOrderByEntryDateDesc(any()) } returns
      summary(ytdStart, BigDecimal("9000"))
    every { transactionRepository.findAllByDateRangeWithInstruments(any(), any()) } returns emptyList()
    every { xirrCalculationService.calculateAdjustedXirr(any(), today) } returns 0.33

    val result = service.calculate(platforms = null)

    val ytdRow = result.windows.first { it.period == "YTD" }
    expect(ytdRow.fromDate).toEqual(ytdStart)
    expect(ytdRow.xirr).notToEqualNull()
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
