package ee.tenman.portfolio.service.calculation

import ch.tutteli.atrium.api.fluent.en_GB.notToBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.DailyPricePoint
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.model.metrics.PortfolioMetrics
import ee.tenman.portfolio.service.pricing.PriceLookup
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.createCashInstrument
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.createInstrument
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal

class PortfolioMetricsCalculationTest : InvestmentMetricsTestBase() {
  @Test
  fun `should calculatePortfolioMetrics with single instrument`() {
    stubPrice("150")

    val metrics = metricsFor(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")))

    expect(metrics.totalValue).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.xirrCashFlows).notToBeEmpty()
  }

  @Test
  fun `should calculatePortfolioMetrics with price lookup matches database price path`() {
    val buy = createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"))
    stubPrice("150")

    val databaseValue = metricsFor(buy).totalValue
    val lookupValue = metricsWithLookup("150", buy).totalValue

    expect(lookupValue).toEqualNumerically(databaseValue)
  }

  @Test
  fun `should calculatePortfolioMetrics with price lookup avoids database query`() {
    metricsWithLookup("150", createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")))

    verify(exactly = 0) { dailyPriceService.getPrice(any(), any()) }
  }

  @Test
  fun `should calculatePortfolioMetrics with a single buy per instrument`() {
    val instrument2 = createInstrument(symbol = "GOOGL", name = "Alphabet Inc.", currentPrice = BigDecimal("2800"), id = 2L)
    stubPrice("150")
    stubPrice("2800", instrument2)

    val metrics =
      metricsFor(
        testInstrument to listOf(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"))),
        instrument2 to listOf(createBuyCashFlow(quantity = BigDecimal("5"), price = BigDecimal("2500"), instrument = instrument2)),
      )

    expect(metrics.totalValue).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.totalProfit).toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
    expect(metrics.xirrCashFlows).toHaveSize(4)
  }

  @ParameterizedTest
  @CsvSource("10, 10, 120", "10, 15, 120", "50, 50, 150")
  fun `should calculatePortfolioMetrics report zero total value when the position is fully or over-sold`(
    buyQuantity: BigDecimal,
    sellQuantity: BigDecimal,
    sellPrice: BigDecimal,
  ) {
    val metrics =
      metricsFor(
        createBuyCashFlow(quantity = buyQuantity, price = BigDecimal("100")),
        createSellCashFlow(quantity = sellQuantity, price = sellPrice),
      )

    expect(metrics.totalValue).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculatePortfolioMetrics count realized loss of a fully closed position`() {
    expect(closedLoserMetrics().totalProfit).toEqualNumerically(BigDecimal("-298.28"))
  }

  @Test
  fun `should calculatePortfolioMetrics feed a fully closed loser into the xirr cash flows`() {
    expect(closedLoserMetrics().xirrCashFlows).toHaveSize(3)
  }

  private fun closedLoserMetrics(): PortfolioMetrics {
    val sell = createSellCashFlow(quantity = BigDecimal("10"), price = BigDecimal("70"))
    sell.realizedProfit = BigDecimal("-298.28")
    every { transactionService.calculateTransactionProfits(any(), any()) } returns Unit
    return metricsFor(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")), sell)
  }

  @Test
  fun `should calculatePortfolioMetrics with fallback when unified calculation fails`() {
    stubFallbackPrice("150")

    val metrics = metricsFor(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")))

    expect(metrics.totalValue).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.xirrCashFlows).notToBeEmpty()
  }

  @Test
  fun `should calculatePortfolioMetrics aggregates multiple instruments with mixed transactions`() {
    val instrument2 = createInstrument(symbol = "TSLA", name = "Tesla Inc.", currentPrice = BigDecimal("700"), id = 2L)
    stubPrice("150")
    stubPrice("700", instrument2)

    val metrics =
      metricsFor(
        testInstrument to
          listOf(
            createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")),
            createSellCashFlow(quantity = BigDecimal("3"), price = BigDecimal("150")),
          ),
        instrument2 to listOf(createBuyCashFlow(quantity = BigDecimal("5"), price = BigDecimal("600"), instrument = instrument2)),
      )

    expect(metrics.totalValue).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.totalProfit).toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
    expect(metrics.xirrCashFlows.size).toBeGreaterThan(3)
  }

  @Test
  fun `should calculatePortfolioMetrics with empty instrument groups returns empty metrics`() {
    val metrics = investmentMetricsService.calculatePortfolioMetrics(emptyMap(), testDate)

    expect(metrics.totalValue).toEqualNumerically(BigDecimal.ZERO)
    expect(metrics.totalProfit).toEqualNumerically(BigDecimal.ZERO)
    expect(metrics.xirrCashFlows).toBeEmpty()
  }

  @Test
  fun `should calculatePortfolioMetrics fallback with only buy transactions`() {
    stubFallbackPrice("150")

    val metrics =
      metricsFor(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createBuyCashFlow(quantity = BigDecimal("5"), price = BigDecimal("110")),
      )

    expect(metrics.totalValue).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.totalProfit).toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
  }

  @Test
  fun `should calculatePortfolioMetrics fallback with buy and sell transactions`() {
    stubFallbackPrice("80")

    val metrics =
      metricsFor(
        createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50")),
        createSellCashFlow(quantity = BigDecimal("40"), price = BigDecimal("70")),
      )

    expect(metrics.totalValue).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.totalProfit).toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
  }

  @Test
  fun `should calculatePortfolioMetrics fallback with zero buy quantity`() {
    val metrics = metricsFor(createSellCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")))

    expect(metrics.totalValue).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculatePortfolioMetrics fallback calculates realized gains correctly`() {
    stubFallbackPrice("90")

    val metrics =
      metricsFor(
        createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50"), commission = BigDecimal("10")),
        createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("60"), commission = BigDecimal("10")),
        createSellCashFlow(quantity = BigDecimal("50"), price = BigDecimal("80"), commission = BigDecimal("5")),
      )

    expect(metrics.totalProfit).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculatePortfolioMetrics fallback with high commission reduces profit`() {
    stubFallbackPrice("160")

    val metrics =
      metricsFor(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"), commission = BigDecimal("50")),
        createSellCashFlow(quantity = BigDecimal("5"), price = BigDecimal("150"), commission = BigDecimal("30")),
      )

    expect(metrics.totalValue).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.totalProfit).toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
  }

  @Test
  fun `should calculatePortfolioMetrics with all platforms having zero or negative holdings`() {
    val metrics =
      metricsFor(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
        createSellCashFlow(quantity = BigDecimal("10"), price = BigDecimal("120"), platform = Platform.LHV),
        createBuyCashFlow(quantity = BigDecimal("5"), price = BigDecimal("110"), platform = Platform.LIGHTYEAR),
        createSellCashFlow(quantity = BigDecimal("5"), price = BigDecimal("130"), platform = Platform.LIGHTYEAR),
      )

    expect(metrics.totalValue).toEqualNumerically(BigDecimal.ZERO)
    expect(metrics.totalProfit).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculatePortfolioMetrics include cash instrument without database lookup`() {
    val cashInstrument = createCashInstrument()
    stubPrice("150")

    val metrics =
      metricsFor(
        cashInstrument to listOf(createBuyCashFlow(quantity = BigDecimal("2000"), price = BigDecimal("1"), instrument = cashInstrument)),
        testInstrument to listOf(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"))),
      )

    expect(metrics.totalValue).toBeGreaterThan(BigDecimal("2000"))
    expect(metrics.xirrCashFlows).notToBeEmpty()
  }

  @Test
  fun `should calculatePortfolioMetrics fallback use price ONE for cash instrument`() {
    val cashInstrument = createCashInstrument()

    val metrics =
      metricsFor(
        cashInstrument to listOf(createBuyCashFlow(quantity = BigDecimal("1500"), price = BigDecimal("1"), instrument = cashInstrument)),
      )

    expect(metrics.totalValue).toEqualNumerically(BigDecimal("1500"))
  }

  private fun metricsFor(vararg transactions: PortfolioTransaction): PortfolioMetrics =
    investmentMetricsService.calculatePortfolioMetrics(mapOf(testInstrument to transactions.toList()), testDate)

  private fun metricsFor(vararg groups: Pair<Instrument, List<PortfolioTransaction>>): PortfolioMetrics =
    investmentMetricsService.calculatePortfolioMetrics(groups.toMap(), testDate)

  private fun metricsWithLookup(
    price: String,
    vararg transactions: PortfolioTransaction,
  ): PortfolioMetrics =
    investmentMetricsService.calculatePortfolioMetrics(
      mapOf(testInstrument to transactions.toList()),
      testDate,
      PriceLookup(listOf(DailyPricePoint(testInstrument.id, testDate, BigDecimal(price)))),
    )

  private fun stubPrice(
    price: String,
    instrument: Instrument = testInstrument,
  ) {
    every { dailyPriceService.getPrice(instrument, any()) } returns BigDecimal(price)
  }

  private fun stubFallbackPrice(price: String) {
    every { dailyPriceService.getPrice(testInstrument, any()) } throws RuntimeException("Unified calc failed") andThen BigDecimal(price)
  }
}
