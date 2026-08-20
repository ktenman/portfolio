package ee.tenman.portfolio.service.calculation

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.model.FinancialConstants.CALCULATION_SCALE
import ee.tenman.portfolio.model.holding.CurrentHoldings
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal
import java.math.RoundingMode

class InvestmentMetricsHoldingsTest : InvestmentMetricsTestBase() {
  @Test
  fun `should calculateCurrentHoldings with single buy transaction`() {
    val holdings = holdingsOf(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")))

    expect(holdings.quantity).toEqualNumerically(BigDecimal("10"))
    expect(holdings.averageCost).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should calculateCurrentHoldings with multiple buy transactions`() {
    val holdings =
      holdingsOf(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"), date = testDate.minusDays(10)),
        createBuyCashFlow(quantity = BigDecimal("5"), price = BigDecimal("120"), date = testDate.minusDays(5)),
      )

    expect(holdings.quantity).toEqualNumerically(BigDecimal("15"))
    expect(holdings.averageCost)
      .toEqualNumerically(BigDecimal("1600").divide(BigDecimal("15"), CALCULATION_SCALE, RoundingMode.HALF_UP))
  }

  @ParameterizedTest
  @CsvSource("40, 60", "25, 75")
  fun `should calculateCurrentHoldings reduces the quantity by the amount sold`(
    sold: BigDecimal,
    expectedQuantity: BigDecimal,
  ) {
    val holdings =
      holdingsOf(
        createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50")),
        createSellCashFlow(quantity = sold, price = BigDecimal("60")),
      )

    expect(holdings.quantity).toEqualNumerically(expectedQuantity)
    expect(holdings.averageCost).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateCurrentHoldings with complete sell-off`() {
    val holdings =
      holdingsOf(
        createBuyCashFlow(quantity = BigDecimal("50"), price = BigDecimal("100")),
        createSellCashFlow(quantity = BigDecimal("50"), price = BigDecimal("120")),
      )

    expect(holdings.quantity).toEqualNumerically(BigDecimal.ZERO)
    expect(holdings.averageCost).toEqualNumerically(BigDecimal.ZERO)
  }

  @ParameterizedTest
  @CsvSource("0", "10")
  fun `should calculateCurrentHoldings excludes commission from the average cost`(commission: BigDecimal) {
    val holdings = holdingsOf(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"), commission = commission))

    expect(holdings.quantity).toEqualNumerically(BigDecimal("10"))
    expect(holdings.averageCost).toEqualNumerically(BigDecimal("100"))
  }

  @ParameterizedTest
  @CsvSource("100, 150.50, 15050.00", "0, 150, 0")
  fun `should calculateCurrentValue as holdings multiplied by current price`(
    holdings: BigDecimal,
    currentPrice: BigDecimal,
    expectedValue: BigDecimal,
  ) {
    val result = investmentMetricsService.calculateCurrentValue(holdings, currentPrice)

    expect(result).toEqualNumerically(expectedValue)
  }

  @ParameterizedTest
  @CsvSource("100, 50, 75, 2500", "100, 80, 60, -2000", "100, 50, 50, 0")
  fun `should calculateProfit as holdings multiplied by the gain over average cost`(
    holdings: BigDecimal,
    averageCost: BigDecimal,
    currentPrice: BigDecimal,
    expectedProfit: BigDecimal,
  ) {
    val profit = investmentMetricsService.calculateProfit(holdings, averageCost, currentPrice)

    expect(profit).toEqualNumerically(expectedProfit)
  }

  @Test
  fun `should calculateCurrentHoldings with sell before buy returns zero`() {
    val holdings =
      holdingsOf(
        createSellCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createBuyCashFlow(quantity = BigDecimal("20"), price = BigDecimal("90")),
      )

    expect(holdings.quantity).toEqualNumerically(BigDecimal("20"))
    expect(holdings.averageCost).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateCurrentHoldings with multiple consecutive sells`() {
    val holdings =
      holdingsOf(
        createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50")),
        createSellCashFlow(quantity = BigDecimal("25"), price = BigDecimal("60")),
        createSellCashFlow(quantity = BigDecimal("25"), price = BigDecimal("65")),
        createSellCashFlow(quantity = BigDecimal("25"), price = BigDecimal("70")),
      )

    expect(holdings.quantity).toEqualNumerically(BigDecimal("25"))
    expect(holdings.averageCost).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateCurrentHoldings with very small quantities`() {
    val holdings =
      holdingsOf(
        createBuyCashFlow(quantity = BigDecimal("0.001"), price = BigDecimal("50000")),
        createSellCashFlow(quantity = BigDecimal("0.0005"), price = BigDecimal("55000")),
      )

    expect(holdings.quantity).toEqualNumerically(BigDecimal("0.0005"))
    expect(holdings.averageCost).toBeGreaterThan(BigDecimal.ZERO)
  }

  private fun holdingsOf(vararg transactions: PortfolioTransaction): CurrentHoldings =
    investmentMetricsService.calculateCurrentHoldings(transactions.toList())
}
