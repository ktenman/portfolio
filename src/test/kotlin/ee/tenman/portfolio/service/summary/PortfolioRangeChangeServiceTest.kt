package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TimeRange
import ee.tenman.portfolio.dto.PortfolioSummaryDto
import ee.tenman.portfolio.service.transaction.TransactionService
import ee.tenman.portfolio.testing.fixture.TransactionFixtures
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.ZERO_COMMISSION
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
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
  private val instrument = TransactionFixtures.createInstrument("TÖÖ:TLN", "Tööstus", "ETF", "EUR")
  private val service =
    PortfolioRangeChangeService(
      seriesService,
      currentDaySummaryCacheService,
      platformSummaryCacheService,
      transactionService,
      clock,
    )

  @BeforeEach
  fun setUp() {
    every { transactionService.getAllTransactions(any<List<String>>()) } returns emptyList()
  }

  @Test
  fun `should report the whole profit when the range opens before the first recorded day`() {
    givenCurrentSummary()
    every { seriesService.getSeries(TimeRange.ONE_YEAR, null) } returns
      listOf(point(LocalDate.of(2026, 6, 16), BigDecimal("5897.21"), BigDecimal("-102.79")))

    expect(service.calculate(TimeRange.ONE_YEAR, null).changeAmount).toEqualNumerically(BigDecimal("818.96"))
  }

  @Test
  fun `should subtract the profit recorded on the range start date`() {
    givenCurrentSummary()
    every { seriesService.getSeries(TimeRange.ONE_WEEK, null) } returns
      listOf(point(LocalDate.of(2026, 8, 7), BigDecimal("24138.46"), BigDecimal("424.52")))

    expect(service.calculate(TimeRange.ONE_WEEK, null).changeAmount).toEqualNumerically(BigDecimal("394.44"))
  }

  @Test
  fun `should divide the change by the value held when the range opened`() {
    givenOpeningYear()

    expect(service.calculate(TimeRange.ONE_YEAR, null).changePercent).toEqualNumerically(BigDecimal("15.6303"))
  }

  @Test
  fun `should ignore money paid in before the range opened`() {
    givenTransactions(buy(LocalDate.of(2025, 1, 5), BigDecimal("1000.00")))
    givenOpeningYear()

    expect(service.calculate(TimeRange.ONE_YEAR, null).changePercent).toEqualNumerically(BigDecimal("15.6303"))
  }

  @Test
  fun `should add money paid in during the range to the divisor`() {
    givenTransactions(buy(LocalDate.of(2026, 3, 9), BigDecimal("1000.00")))
    givenOpeningYear()

    expect(service.calculate(TimeRange.ONE_YEAR, null).changePercent).toEqualNumerically(BigDecimal("13.3641"))
  }

  @Test
  fun `should leave the divisor alone when money was only taken out during the range`() {
    givenTransactions(sell(LocalDate.of(2026, 3, 9), BigDecimal("1000.00")))
    givenOpeningYear()

    expect(service.calculate(TimeRange.ONE_YEAR, null).changePercent).toEqualNumerically(BigDecimal("15.6303"))
  }

  @Test
  fun `should count only the surplus when a sale funds a purchase on the same day`() {
    givenTransactions(
      buy(LocalDate.of(2026, 3, 9), BigDecimal("1500.00")),
      sell(LocalDate.of(2026, 3, 9), BigDecimal("500.00")),
    )
    givenOpeningYear()

    expect(service.calculate(TimeRange.ONE_YEAR, null).changePercent).toEqualNumerically(BigDecimal("13.3641"))
  }

  @Test
  fun `should divide the max range by everything ever paid in`() {
    givenTransactions(
      buy(LocalDate.of(2024, 11, 4), BigDecimal("4000.00")),
      sell(LocalDate.of(2025, 2, 17), BigDecimal("3000.00")),
      buy(LocalDate.of(2025, 10, 2), BigDecimal("2000.00")),
    )
    givenCurrentSummary(BigDecimal("7500.00"), BigDecimal("1500.00"))

    expect(service.calculate(TimeRange.MAX, null).changePercent).toEqualNumerically(BigDecimal("25"))
  }

  @Test
  fun `should treat the max range as opening from zero profit`() {
    givenCurrentSummary()

    expect(service.calculate(TimeRange.MAX, null).changeAmount).toEqualNumerically(BigDecimal("818.96"))
  }

  @Test
  fun `should report a zero percentage when no capital was ever at work`() {
    givenCurrentSummary(BigDecimal.ZERO, BigDecimal("500.00"))

    expect(service.calculate(TimeRange.MAX, null).changePercent).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should report a zero change when the range has no recorded days`() {
    givenCurrentSummary(BigDecimal.ZERO, BigDecimal.ZERO)
    every { seriesService.getSeries(TimeRange.SIX_MONTHS, null) } returns emptyList()

    expect(service.calculate(TimeRange.SIX_MONTHS, null).changeAmount).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should read the current summary of the selected platforms`() {
    val platforms = listOf(Platform.LIGHTYEAR_BUSINESS)
    every { platformSummaryCacheService.getCurrentDaySummaryForPlatforms(platforms) } returns
      summary(LocalDate.of(2026, 8, 14), BigDecimal("24532.90"), BigDecimal("818.96"))
    every { seriesService.getSeries(TimeRange.FIVE_YEARS, platforms) } returns
      listOf(point(LocalDate.of(2026, 6, 16), BigDecimal("5897.21"), BigDecimal("-102.79")))

    expect(service.calculate(TimeRange.FIVE_YEARS, platforms).changeAmount).toEqualNumerically(BigDecimal("818.96"))
  }

  private fun givenTransactions(vararg transactions: PortfolioTransaction) {
    every { transactionService.getAllTransactions(any<List<String>>()) } returns transactions.toList()
  }

  private fun givenCurrentSummary(
    totalValue: BigDecimal = BigDecimal("24532.90"),
    totalProfit: BigDecimal = BigDecimal("818.96"),
  ) {
    every { currentDaySummaryCacheService.getCurrentDaySummary() } returns
      summary(LocalDate.of(2026, 8, 14), totalValue, totalProfit)
  }

  private fun givenOpeningYear() {
    givenCurrentSummary()
    every { seriesService.getSeries(TimeRange.ONE_YEAR, null) } returns
      listOf(point(LocalDate.of(2025, 8, 14), BigDecimal("5897.21"), BigDecimal("-102.79")))
  }

  private fun buy(
    date: LocalDate,
    amount: BigDecimal,
  ): PortfolioTransaction =
    TransactionFixtures.createBuyTransaction(instrument, BigDecimal.ONE, amount, date, Platform.LIGHTYEAR, ZERO_COMMISSION)

  private fun sell(
    date: LocalDate,
    amount: BigDecimal,
  ): PortfolioTransaction =
    TransactionFixtures.createSellTransaction(instrument, BigDecimal.ONE, amount, date, Platform.LIGHTYEAR, ZERO_COMMISSION)

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
