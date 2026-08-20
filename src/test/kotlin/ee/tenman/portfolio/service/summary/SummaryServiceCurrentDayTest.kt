package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Platform
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class SummaryServiceCurrentDayTest : SummaryServiceTestBase() {
  @Test
  fun `getCurrentDaySummary should always reflect current instrument data`() {
    val testTransaction = createBuyTransaction(BigDecimal("793.00"), BigDecimal("29.81"), testDate.minusDays(30))
    every { transactionService.getAllTransactions() } returns listOf(testTransaction)
    stubMetrics(testDate, BigDecimal("21870.94"), BigDecimal("-1762.39"))

    val summary = summaryService.getCurrentDaySummary()

    expect(summary.totalProfit).toEqualNumerically(BigDecimal("-1762.39"))
    expect(summary.earningsPerDay).toEqualNumerically(BigDecimal("0E-10"))
  }

  @Test
  fun `getCurrentDaySummary should reflect xirr from instruments`() {
    val testTransaction = createBuyTransaction(BigDecimal("10"), BigDecimal("50.00"), testDate.minusDays(30))
    every { transactionService.getAllTransactions() } returns listOf(testTransaction)
    stubMetrics(testDate, BigDecimal("600.00"), BigDecimal("100.00"))
    every { xirrCalculationService.calculateAdjustedXirr(any(), testDate) } returns 0.075

    val summary = summaryService.getCurrentDaySummary()

    expect(summary.totalValue).toEqualNumerically(BigDecimal("600.00"))
    expect(summary.totalProfit).toEqualNumerically(BigDecimal("100.00"))
    expect(summary.xirrAnnualReturn).toEqualNumerically(BigDecimal("0.07500000"))
    expect(summary.earningsPerDay).toEqualNumerically(expectedEarningsPerDay(summary.totalValue, summary.xirrAnnualReturn))
  }

  @Test
  fun `should getCurrentDaySummaryForPlatforms return summary for filtered platforms`() {
    val testTransaction = createBuyTransaction(BigDecimal("10"), BigDecimal("100"), testDate.minusDays(5), Platform.LIGHTYEAR)
    every { transactionService.getAllTransactions(listOf("LIGHTYEAR")) } returns listOf(testTransaction)
    stubMetrics(testDate, BigDecimal("1200.00"), BigDecimal("200.00"))
    every { xirrCalculationService.calculateAdjustedXirr(any(), testDate) } returns 0.08

    val result = summaryService.getCurrentDaySummaryForPlatforms(listOf(Platform.LIGHTYEAR))

    expect(result.totalValue).toEqualNumerically(BigDecimal("1200.00"))
    expect(result.totalProfit).toEqualNumerically(BigDecimal("200.00"))
    verify { transactionService.getAllTransactions(listOf("LIGHTYEAR")) }
  }

  @Test
  fun `should getHistoricalSummariesForPlatforms return paginated summaries`() {
    val testTransaction = createBuyTransaction(BigDecimal("10"), BigDecimal("100"), LocalDate.of(2025, 5, 6), Platform.LIGHTYEAR)
    every { transactionService.getAllTransactions(listOf("LIGHTYEAR")) } returns listOf(testTransaction)
    val summaryDates =
      listOf(
        LocalDate.of(2025, 5, 6),
        LocalDate.of(2025, 5, 7),
        LocalDate.of(2025, 5, 8),
        LocalDate.of(2025, 5, 9),
      )
    every { summaryBatchProcessor.calculateSummaries(any(), any()) } returns
      summaryDates.map { date -> createSummary(date, "1100.00", "0.05", "100.00", "0.01") }

    val result = summaryService.getHistoricalSummariesForPlatforms(listOf(Platform.LIGHTYEAR), 0, 10)

    expect(result.totalElements).toEqual(4L)
    expect(result.content).toHaveSize(4)
    expect(result.content.first().entryDate).toEqual(LocalDate.of(2025, 5, 9))
    expect(result.content.last().entryDate).toEqual(LocalDate.of(2025, 5, 6))
  }

  @Test
  fun `should getHistoricalSummariesForPlatforms return empty page when no transactions`() {
    every { transactionService.getAllTransactions(listOf("LIGHTYEAR")) } returns emptyList()

    val result = summaryService.getHistoricalSummariesForPlatforms(listOf(Platform.LIGHTYEAR), 0, 10)

    expect(result.isEmpty).toEqual(true)
    expect(result.totalElements).toEqual(0L)
  }
}
