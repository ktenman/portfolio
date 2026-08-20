package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TimeRange
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.dto.PortfolioSummaryDto
import ee.tenman.portfolio.service.transaction.TransactionService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PortfolioRangeChangeServiceTest {
  private val seriesService = mockk<PortfolioSummarySeriesService>()
  private val currentDaySummaryCacheService = mockk<CurrentDaySummaryCacheService>()
  private val platformSummaryCacheService = mockk<PlatformSummaryCacheService>()
  private val transactionService = mockk<TransactionService>()
  private val clock = Clock.fixed(Instant.parse("2026-08-14T12:00:00Z"), ZoneId.of("UTC"))
  private val service =
    PortfolioRangeChangeService(
      seriesService,
      currentDaySummaryCacheService,
      platformSummaryCacheService,
      transactionService,
      clock,
    )

  @Test
  fun `should report the whole profit when the range opens before the first recorded day`() {
    givenTransactions()
    every { currentDaySummaryCacheService.getCurrentDaySummary() } returns
      summary(LocalDate.of(2026, 8, 14), BigDecimal("24532.90"), BigDecimal("818.96"))
    every { seriesService.getSeries(TimeRange.ONE_YEAR, null) } returns
      listOf(point(LocalDate.of(2026, 6, 16), BigDecimal("5897.21"), BigDecimal("-102.79")))

    expect(service.calculate(TimeRange.ONE_YEAR, null).changeAmount).toEqualNumerically(BigDecimal("818.96"))
  }

  @Test
  fun `should subtract the profit recorded on the range start date`() {
    givenTransactions()
    every { currentDaySummaryCacheService.getCurrentDaySummary() } returns
      summary(LocalDate.of(2026, 8, 14), BigDecimal("24532.90"), BigDecimal("818.96"))
    every { seriesService.getSeries(TimeRange.ONE_WEEK, null) } returns
      listOf(point(LocalDate.of(2026, 8, 7), BigDecimal("24138.46"), BigDecimal("424.52")))

    expect(service.calculate(TimeRange.ONE_WEEK, null).changeAmount).toEqualNumerically(BigDecimal("394.44"))
  }

  @Test
  fun `should divide the change by the value held when the range opened`() {
    givenTransactions()
    givenOpeningYearOf(BigDecimal("5897.21"))

    expect(service.calculate(TimeRange.ONE_YEAR, null).changePercent).toEqualNumerically(BigDecimal("15.6303"))
  }

  @Test
  fun `should add money paid in during the range to the divisor`() {
    givenTransactions(buy(LocalDate.of(2026, 3, 9), BigDecimal("1000.00")))
    givenOpeningYearOf(BigDecimal("5897.21"))

    expect(service.calculate(TimeRange.ONE_YEAR, null).changePercent).toEqualNumerically(BigDecimal("13.3641"))
  }

  @Test
  fun `should leave the divisor alone when money was only taken out during the range`() {
    givenTransactions(sell(LocalDate.of(2026, 3, 9), BigDecimal("1000.00")))
    givenOpeningYearOf(BigDecimal("5897.21"))

    expect(service.calculate(TimeRange.ONE_YEAR, null).changePercent).toEqualNumerically(BigDecimal("15.6303"))
  }

  @Test
  fun `should count only the surplus when a sale funds a purchase on the same day`() {
    givenTransactions(
      buy(LocalDate.of(2026, 3, 9), BigDecimal("1500.00")),
      sell(LocalDate.of(2026, 3, 9), BigDecimal("500.00")),
    )
    givenOpeningYearOf(BigDecimal("5897.21"))

    expect(service.calculate(TimeRange.ONE_YEAR, null).changePercent).toEqualNumerically(BigDecimal("13.3641"))
  }

  @Test
  fun `should divide the max range by everything ever paid in`() {
    givenTransactions(
      buy(LocalDate.of(2024, 11, 4), BigDecimal("4000.00")),
      sell(LocalDate.of(2025, 2, 17), BigDecimal("3000.00")),
      buy(LocalDate.of(2025, 10, 2), BigDecimal("2000.00")),
    )
    every { currentDaySummaryCacheService.getCurrentDaySummary() } returns
      summary(LocalDate.of(2026, 8, 14), BigDecimal("7500.00"), BigDecimal("1500.00"))

    expect(service.calculate(TimeRange.MAX, null).changePercent).toEqualNumerically(BigDecimal("25"))
  }

  @Test
  fun `should treat the max range as opening from zero profit`() {
    givenTransactions()
    every { currentDaySummaryCacheService.getCurrentDaySummary() } returns
      summary(LocalDate.of(2026, 8, 14), BigDecimal("24532.90"), BigDecimal("818.96"))

    expect(service.calculate(TimeRange.MAX, null).changeAmount).toEqualNumerically(BigDecimal("818.96"))
  }

  @Test
  fun `should report a zero percentage when no capital was ever at work`() {
    givenTransactions()
    every { currentDaySummaryCacheService.getCurrentDaySummary() } returns
      summary(LocalDate.of(2026, 8, 14), BigDecimal.ZERO, BigDecimal("500.00"))

    expect(service.calculate(TimeRange.MAX, null).changePercent).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should report a zero change when the range has no recorded days`() {
    givenTransactions()
    every { currentDaySummaryCacheService.getCurrentDaySummary() } returns
      summary(LocalDate.of(2026, 8, 14), BigDecimal.ZERO, BigDecimal.ZERO)
    every { seriesService.getSeries(TimeRange.SIX_MONTHS, null) } returns emptyList()

    expect(service.calculate(TimeRange.SIX_MONTHS, null).changeAmount).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should read the current summary of the selected platforms`() {
    val platforms = listOf(Platform.LIGHTYEAR_BUSINESS)
    every { transactionService.getAllTransactions(listOf("LIGHTYEAR_BUSINESS"), any(), any()) } returns emptyList()
    every { platformSummaryCacheService.getCurrentDaySummaryForPlatforms(platforms) } returns
      summary(LocalDate.of(2026, 8, 14), BigDecimal("24532.90"), BigDecimal("818.96"))
    every { seriesService.getSeries(TimeRange.FIVE_YEARS, platforms) } returns
      listOf(point(LocalDate.of(2026, 6, 16), BigDecimal("5897.21"), BigDecimal("-102.79")))

    expect(service.calculate(TimeRange.FIVE_YEARS, platforms).changeAmount).toEqualNumerically(BigDecimal("818.96"))
  }

  private fun givenTransactions(vararg transactions: PortfolioTransaction) {
    every { transactionService.getAllTransactions(null, any(), any()) } returns transactions.toList()
  }

  private fun givenOpeningYearOf(openingValue: BigDecimal) {
    every { currentDaySummaryCacheService.getCurrentDaySummary() } returns
      summary(LocalDate.of(2026, 8, 14), BigDecimal("24532.90"), BigDecimal("818.96"))
    every { seriesService.getSeries(TimeRange.ONE_YEAR, null) } returns
      listOf(point(LocalDate.of(2025, 8, 14), openingValue, BigDecimal("-102.79")))
  }

  private fun buy(
    date: LocalDate,
    amount: BigDecimal,
  ): PortfolioTransaction = transaction(date, amount, TransactionType.BUY)

  private fun sell(
    date: LocalDate,
    amount: BigDecimal,
  ): PortfolioTransaction = transaction(date, amount, TransactionType.SELL)

  private fun transaction(
    date: LocalDate,
    amount: BigDecimal,
    type: TransactionType,
  ): PortfolioTransaction =
    PortfolioTransaction(
      instrument = Instrument("TÖÖ:TLN", "Tööstus", "ETF", "EUR"),
      transactionType = type,
      quantity = BigDecimal.ONE,
      price = amount,
      transactionDate = date,
      platform = Platform.LIGHTYEAR,
    )

  private fun summary(
    entryDate: LocalDate,
    totalValue: BigDecimal,
    totalProfit: BigDecimal,
  ): PortfolioDailySummary =
    PortfolioDailySummary(
      entryDate = entryDate,
      totalValue = totalValue,
      xirrAnnualReturn = BigDecimal("0.24"),
      totalProfit = totalProfit,
      earningsPerDay = BigDecimal("16.14"),
    )

  private fun point(
    date: LocalDate,
    totalValue: BigDecimal,
    totalProfit: BigDecimal,
  ): PortfolioSummaryDto =
    PortfolioSummaryDto(
      date = date,
      totalValue = totalValue,
      xirrAnnualReturn = BigDecimal("0.24"),
      realizedProfit = BigDecimal.ZERO,
      unrealizedProfit = totalProfit,
      totalProfit = totalProfit,
      earningsPerDay = BigDecimal("16.14"),
      earningsPerMonth = BigDecimal("491.33"),
    )
}
