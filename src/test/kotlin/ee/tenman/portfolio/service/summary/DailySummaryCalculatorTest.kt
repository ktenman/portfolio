package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.model.metrics.PortfolioMetrics
import ee.tenman.portfolio.service.calculation.InvestmentMetricsService
import ee.tenman.portfolio.service.calculation.XirrCalculationService
import ee.tenman.portfolio.service.pricing.PriceLookup
import ee.tenman.portfolio.testing.fixture.TransactionFixtures
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class DailySummaryCalculatorTest {
  private val investmentMetricsService = mockk<InvestmentMetricsService>()
  private val xirrCalculationService = mockk<XirrCalculationService>(relaxed = true)
  private val calculator = DailySummaryCalculator(investmentMetricsService, xirrCalculationService)
  private val date = LocalDate.of(2026, 9, 13)
  private val instrument = TransactionFixtures.createInstrument()

  @Test
  fun `earnings per day is total profit divided by portfolio age when younger than a year`() {
    stubProfit(date, "100")

    val summary = calculator.calculateFromTransactions(listOf(buy(date.minusDays(10))), date)

    expect(summary.earningsPerDay).toEqualNumerically(BigDecimal("10"))
  }

  @Test
  fun `earnings per day divides by a single day on the inception date`() {
    stubProfit(date, "5")

    val summary = calculator.calculateFromTransactions(listOf(buy(date)), date)

    expect(summary.earningsPerDay).toEqualNumerically(BigDecimal("5"))
  }

  @Test
  fun `earnings per day is the trailing twelve month profit change divided by 365 after the first year`() {
    stubProfit(date, "1000")
    stubProfit(date.minusDays(365), "270")

    val summary = calculator.calculateFromTransactions(listOf(buy(date.minusDays(500))), date)

    expect(summary.earningsPerDay).toEqualNumerically(BigDecimal("2"))
  }

  @Test
  fun `earnings per day uses the inception day profit as baseline at exactly one year`() {
    stubProfit(date, "364")
    stubProfit(date.minusDays(365), "-1")

    val summary = calculator.calculateFromTransactions(listOf(buy(date.minusDays(365))), date)

    expect(summary.earningsPerDay).toEqualNumerically(BigDecimal("1"))
  }

  @Test
  fun `earnings per day is negative when the trailing twelve month profit fell`() {
    stubProfit(date, "100")
    stubProfit(date.minusDays(365), "465")

    val summary = calculator.calculateFromTransactions(listOf(buy(date.minusDays(400))), date)

    expect(summary.earningsPerDay).toEqualNumerically(BigDecimal("-1"))
  }

  @Test
  fun `baseline metrics receive the same price lookup as the current metrics`() {
    val lookup = PriceLookup(emptyList())
    every { investmentMetricsService.calculatePortfolioMetrics(any(), date.minusDays(365), lookup) } returns metrics("270")
    every { investmentMetricsService.calculatePortfolioMetrics(any(), date, lookup) } returns metrics("1000")

    val summary = calculator.calculateFromTransactions(listOf(buy(date.minusDays(500))), date, lookup)

    expect(summary.earningsPerDay).toEqualNumerically(BigDecimal("2"))
  }

  @Test
  fun `baseline profit only counts transactions made by the baseline date`() {
    every {
      investmentMetricsService.calculatePortfolioMetrics(match { it.values.flatten().size == 1 }, date.minusDays(365), null)
    } returns metrics("50")
    stubProfit(date, "415")

    val summary = calculator.calculateFromTransactions(listOf(buy(date.minusDays(400)), buy(date.minusDays(100))), date)

    expect(summary.earningsPerDay).toEqualNumerically(BigDecimal("1"))
  }

  private fun stubProfit(
    on: LocalDate,
    profit: String,
  ) {
    every { investmentMetricsService.calculatePortfolioMetrics(any(), on, null) } returns metrics(profit)
  }

  private fun metrics(profit: String) = PortfolioMetrics(totalProfit = BigDecimal(profit))

  private fun buy(on: LocalDate): PortfolioTransaction =
    TransactionFixtures.createBuyTransaction(instrument, BigDecimal.ONE, BigDecimal.ONE, on)
}
