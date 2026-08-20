package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.PortfolioDailySummary
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class SummaryServiceDateResolutionTest : SummaryServiceTestBase() {
  @Test
  fun `calculateSummaryForDate should return existing summary for historical date when found`() {
    val historicalDate = LocalDate.of(2024, 6, 15)
    val existingSummary = createSummary(historicalDate, "5000.00", "0.12", "500.00", "1.64")

    every { summaryCacheService.findByEntryDate(historicalDate) } returns existingSummary

    val result = summaryService.calculateSummaryForDate(historicalDate)

    expect(result).toEqual(existingSummary)
    verify(exactly = 0) { transactionService.getAllTransactions() }
  }

  @Test
  fun `calculateSummaryForDate should use today branch when date is today`() {
    val today = testDate
    every { summaryCacheService.findByEntryDate(today) } returns null

    val testTransaction = createBuyTransaction(BigDecimal("10"), BigDecimal("100"), today.minusDays(5))
    every { transactionService.getAllTransactions() } returns listOf(testTransaction)

    stubMetrics(today, BigDecimal("1200.00"), BigDecimal("200.00"))
    every { xirrCalculationService.calculateAdjustedXirr(any(), today) } returns 0.08

    val result = summaryService.calculateSummaryForDate(today)

    expect(result.entryDate).toEqual(today)
    expect(result.totalValue).toEqualNumerically(BigDecimal("1200.00"))
    verify { summaryCacheService.findByEntryDate(today) }
  }

  @Test
  fun `calculateSummaryForDate should align existing today summary with current instruments`() {
    val today = testDate

    val existingSummary =
      createSummary(today, "1000.00", "0.05", "50.00", "0.14").apply {
        id = 456L
        version = 2
      }

    val testTransaction = createBuyTransaction(BigDecimal("10"), BigDecimal("100"), today.minusDays(5))
    every { transactionService.getAllTransactions() } returns listOf(testTransaction)
    every { summaryCacheService.findByEntryDate(today) } returns existingSummary

    stubMetrics(today, BigDecimal("1200.00"), BigDecimal("200.00"))
    every { xirrCalculationService.calculateAdjustedXirr(any(), today) } returns 0.08

    val result = summaryService.calculateSummaryForDate(today)

    expect(result.id).toEqual(456L)
    expect(result.version).toEqual(2)
    expect(result.totalValue).toEqualNumerically(BigDecimal("1200.00"))
    expect(result.totalProfit).toEqualNumerically(BigDecimal("200.00"))
  }

  @Test
  fun `calculateSummaryForDate should use previous day summary when no transactions on date and values match`() {
    val date = LocalDate.of(2024, 7, 15)
    val previousSummary = createSummary(date.minusDays(1), "2000.00", "0.06", "100.00", "0.33")
    stubUneventfulDate(date, previousSummary, totalValue = "2000.00", totalProfit = "100.00", xirr = 0.06)

    val result = summaryService.calculateSummaryForDate(date)

    expect(result.entryDate).toEqual(date)
    expect(result.totalValue).toEqualNumerically(BigDecimal("2000.00"))
    verify { summaryCacheService.findByEntryDate(date.minusDays(1)) }
  }

  @Test
  fun `calculateSummaryForDate should calculate new summary when no transactions on date and no previous summary`() {
    val date = LocalDate.of(2024, 7, 15)
    stubUneventfulDate(date, previousSummary = null, totalValue = "2000.00", totalProfit = "100.00", xirr = 0.06)

    val result = summaryService.calculateSummaryForDate(date)

    expect(result.entryDate).toEqual(date)
    expect(result.totalValue).toEqualNumerically(BigDecimal("2000.00"))
    verify { summaryCacheService.findByEntryDate(date.minusDays(1)) }
    verify { investmentMetricsService.calculatePortfolioMetrics(any(), date) }
  }

  @Test
  fun `calculateSummaryForDate should calculate new summary when values differ from previous day`() {
    val date = LocalDate.of(2024, 7, 15)
    val previousSummary = createSummary(date.minusDays(1), "2000.00", "0.06", "100.00", "0.33")
    stubUneventfulDate(date, previousSummary, totalValue = "2100.00", totalProfit = "150.00", xirr = 0.07)

    val result = summaryService.calculateSummaryForDate(date)

    expect(result.entryDate).toEqual(date)
    expect(result.totalValue).toEqualNumerically(BigDecimal("2100.00"))
    expect(result.totalProfit).toEqualNumerically(BigDecimal("150.00"))
    verify { summaryCacheService.findByEntryDate(date.minusDays(1)) }
  }

  private fun stubUneventfulDate(
    date: LocalDate,
    previousSummary: PortfolioDailySummary?,
    totalValue: String,
    totalProfit: String,
    xirr: Double,
  ) {
    val oldTransaction = createBuyTransaction(BigDecimal("20"), BigDecimal("100"), date.minusDays(5))
    every { transactionService.getAllTransactions() } returns listOf(oldTransaction)
    every { summaryCacheService.findByEntryDate(date) } returns null
    every { summaryCacheService.findByEntryDate(date.minusDays(1)) } returns previousSummary
    stubMetrics(date, BigDecimal(totalValue), BigDecimal(totalProfit))
    every { xirrCalculationService.calculateAdjustedXirr(any(), date) } returns xirr
  }
}
