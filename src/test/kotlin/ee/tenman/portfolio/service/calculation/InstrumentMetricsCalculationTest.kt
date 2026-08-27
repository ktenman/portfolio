package ee.tenman.portfolio.service.calculation

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.model.metrics.InstrumentMetrics
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.createCashInstrument
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal

class InstrumentMetricsCalculationTest : InvestmentMetricsTestBase() {
  @Test
  fun `should calculateInstrumentMetrics with empty transactions returns empty metrics`() {
    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, emptyList(), testDate)

    expect(metrics).toEqual(InstrumentMetrics.EMPTY)
  }

  @Test
  fun `should calculateInstrumentMetrics with single platform calculates correctly`() {
    val metrics = metricsFor(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV))

    expect(metrics.quantity).toEqualNumerically(BigDecimal("10"))
    expect(metrics.totalInvestment).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.currentValue).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateInstrumentMetrics with multiple platforms aggregates correctly`() {
    val metrics =
      metricsFor(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
        createBuyCashFlow(quantity = BigDecimal("15"), price = BigDecimal("110"), platform = Platform.LIGHTYEAR),
      )

    expect(metrics.quantity).toEqualNumerically(BigDecimal("25"))
    expect(metrics.totalInvestment).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateInstrumentMetricsWithProfits calls transaction service`() {
    val metrics = profitMetricsFor(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")))

    expect(metrics.quantity).toEqualNumerically(BigDecimal("10"))
    expect(metrics.totalInvestment).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateInstrumentMetricsWithProfits with empty transactions returns empty metrics`() {
    val metrics = investmentMetricsService.calculateInstrumentMetricsWithProfits(testInstrument, emptyList(), testDate)

    expect(metrics).toEqual(InstrumentMetrics.EMPTY)
  }

  @Test
  fun `should calculateInstrumentMetricsWithProfits with multiple platforms`() {
    val metrics =
      profitMetricsFor(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
        createBuyCashFlow(quantity = BigDecimal("5"), price = BigDecimal("120"), platform = Platform.LIGHTYEAR),
        createSellCashFlow(quantity = BigDecimal("3"), price = BigDecimal("150"), platform = Platform.LHV),
      )

    expect(metrics.quantity).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.totalInvestment).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.currentValue).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateInstrumentMetricsWithProfits with all positions sold`() {
    val metrics =
      profitMetricsFor(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createSellCashFlow(quantity = BigDecimal("10"), price = BigDecimal("150")),
      )

    expect(metrics.quantity).toEqualNumerically(BigDecimal.ZERO)
    expect(metrics.totalInvestment).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateInstrumentMetrics with platform having zero holdings is excluded`() {
    val metrics =
      metricsFor(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
        createSellCashFlow(quantity = BigDecimal("10"), price = BigDecimal("120"), platform = Platform.LHV),
        createBuyCashFlow(quantity = BigDecimal("5"), price = BigDecimal("110"), platform = Platform.LIGHTYEAR),
      )

    expect(metrics.quantity).toEqualNumerically(BigDecimal("5"))
  }

  @Test
  fun `should calculateInstrumentMetrics with one platform having zero holdings after sell`() {
    val metrics =
      metricsFor(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
        createSellCashFlow(quantity = BigDecimal("10"), price = BigDecimal("120"), platform = Platform.LHV),
        createBuyCashFlow(quantity = BigDecimal("5"), price = BigDecimal("110"), platform = Platform.LIGHTYEAR),
        createSellCashFlow(quantity = BigDecimal("2"), price = BigDecimal("130"), platform = Platform.LIGHTYEAR),
      )

    expect(metrics.quantity).toEqualNumerically(BigDecimal("3"))
    expect(metrics.totalInvestment).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateInstrumentMetrics with negative profit scenario`() {
    testInstrument.currentPrice = BigDecimal("50")

    val metrics = metricsFor(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")))

    expect(metrics.profit).toBeLessThan(BigDecimal.ZERO)
    expect(metrics.currentValue).toBeLessThan(metrics.totalInvestment)
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = ["1"])
  fun `should calculateInstrumentMetrics use price ONE for cash instrument`(currentPrice: String?) {
    val cashInstrument = createCashInstrument(currentPrice = currentPrice?.let { BigDecimal(it) })
    val transactions = listOf(createBuyCashFlow(quantity = BigDecimal("1000"), price = BigDecimal("1"), instrument = cashInstrument))

    val metrics = investmentMetricsService.calculateInstrumentMetrics(cashInstrument, transactions, testDate)

    expect(metrics.currentValue).toEqualNumerically(BigDecimal("1000"))
    expect(metrics.quantity).toEqualNumerically(BigDecimal("1000"))
  }

  private fun metricsFor(vararg transactions: PortfolioTransaction): InstrumentMetrics =
    investmentMetricsService.calculateInstrumentMetrics(testInstrument, transactions.toList(), testDate)

  private fun profitMetricsFor(vararg transactions: PortfolioTransaction): InstrumentMetrics =
    investmentMetricsService.calculateInstrumentMetricsWithProfits(testInstrument, transactions.toList(), testDate)
}
