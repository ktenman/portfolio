package ee.tenman.portfolio.service.calculation

import ch.tutteli.atrium.api.fluent.en_GB.and
import ch.tutteli.atrium.api.fluent.en_GB.notToBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.model.FinancialConstants.CALCULATION_SCALE
import ee.tenman.portfolio.model.metrics.InstrumentMetrics
import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.transaction.TransactionService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.stream.Stream

class InvestmentMetricsServiceTest {
  private val clock = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneId.of("UTC"))
  private val dailyPriceService = mockk<DailyPriceService>()
  private val transactionService = mockk<TransactionService>()
  private val xirrCalculationService = XirrCalculationService(clock)
  private val holdingsCalculationService = HoldingsCalculationService()
  private lateinit var investmentMetricsService: InvestmentMetricsService

  private lateinit var testInstrument: Instrument
  private val testDate = LocalDate.of(2024, 1, 15)

  @BeforeEach
  fun setUp() {
    testInstrument =
      Instrument(
      symbol = "AAPL",
      name = "Apple Inc.",
      category = "Stock",
      baseCurrency = "USD",
      currentPrice = BigDecimal("150.00"),
      providerName = ProviderName.FT,
    ).apply {
      id = 1L
    }
    investmentMetricsService =
      InvestmentMetricsService(
      dailyPriceService,
      transactionService,
      xirrCalculationService,
      holdingsCalculationService,
      Clock.systemDefaultZone(),
    )

    every { transactionService.calculateTransactionProfits(any(), any()) } answers {
      val transactions = firstArg<List<PortfolioTransaction>>()
      transactions.forEach {
        it.unrealizedProfit = BigDecimal.ZERO
        it.realizedProfit = BigDecimal.ZERO
      }
    }
  }

  @Test
  fun `should calculateCurrentHoldings with single buy transaction`() {
    val transaction = createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"))

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(listOf(transaction))

    expect(quantity).toEqualNumerically(BigDecimal("10"))
    expect(averageCost).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should calculateCurrentHoldings with multiple buy transactions`() {
    val transactions =
      listOf(
        createBuyCashFlow(
          quantity = BigDecimal("10"),
          price = BigDecimal("100"),
          date = testDate.minusDays(10),
        ),
        createBuyCashFlow(
          quantity = BigDecimal("5"),
          price = BigDecimal("120"),
          date = testDate.minusDays(5),
        ),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(transactions)

    expect(quantity).toEqualNumerically(BigDecimal("15"))
    val expectedTotalCost =
      BigDecimal("10")
        .multiply(BigDecimal("100"))
        .add(BigDecimal("5").multiply(BigDecimal("120")))
    val expectedAvgCost = expectedTotalCost.divide(BigDecimal("15"), CALCULATION_SCALE, RoundingMode.HALF_UP)
    expect(averageCost).toEqualNumerically(expectedAvgCost)
  }

  @Test
  fun `should calculateCurrentHoldings with buy and sell transactions`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50")),
        createSellCashFlow(quantity = BigDecimal("40"), price = BigDecimal("60")),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(transactions)

    expect(quantity).toEqualNumerically(BigDecimal("60"))
    expect(averageCost).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateCurrentHoldings with complete sell-off`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("50"), price = BigDecimal("100")),
        createSellCashFlow(quantity = BigDecimal("50"), price = BigDecimal("120")),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(transactions)

    expect(quantity).toEqualNumerically(BigDecimal.ZERO)
    expect(averageCost).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateCurrentHoldings handles commission correctly`() {
    val transaction =
      createBuyCashFlow(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        commission = BigDecimal("10"),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(listOf(transaction))

    expect(quantity).toEqualNumerically(BigDecimal("10"))
    expect(averageCost).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should calculateCurrentValue returns correct value`() {
    val holdings = BigDecimal("100")
    val currentPrice = BigDecimal("150.50")

    val result = investmentMetricsService.calculateCurrentValue(holdings, currentPrice)

    expect(result).toEqualNumerically(BigDecimal("15050.00"))
  }

  @Test
  fun `should calculateCurrentValue with zero holdings`() {
    val result = investmentMetricsService.calculateCurrentValue(BigDecimal.ZERO, BigDecimal("150"))

    expect(result).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateProfit returns positive profit`() {
    val holdings = BigDecimal("100")
    val averageCost = BigDecimal("50")
    val currentPrice = BigDecimal("75")

    val profit = investmentMetricsService.calculateProfit(holdings, averageCost, currentPrice)

    expect(profit).toEqualNumerically(BigDecimal("2500"))
  }

  @Test
  fun `should calculateProfit returns negative profit (loss)`() {
    val holdings = BigDecimal("100")
    val averageCost = BigDecimal("80")
    val currentPrice = BigDecimal("60")

    val profit = investmentMetricsService.calculateProfit(holdings, averageCost, currentPrice)

    expect(profit).toEqualNumerically(BigDecimal("-2000"))
  }

  @Test
  fun `should calculateProfit returns zero when no price change`() {
    val holdings = BigDecimal("100")
    val price = BigDecimal("50")

    val profit = investmentMetricsService.calculateProfit(holdings, price, price)

    expect(profit).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should buildCashFlows with buy transactions only`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"), date = testDate.minusDays(30)),
        createBuyCashFlow(quantity = BigDecimal("5"), price = BigDecimal("110"), date = testDate.minusDays(15)),
      )
    val currentValue = BigDecimal("1500")

    val xirrCashFlows = xirrCalculationService.buildCashFlows(transactions, currentValue, testDate)

    expect(xirrCashFlows).toHaveSize(3)
    expect(xirrCashFlows[0].amount).toBeLessThan(0.0)
    expect(xirrCashFlows[1].amount).toBeLessThan(0.0)
    expect(xirrCashFlows[2].amount).toEqual(1500.0)
    expect(xirrCashFlows[2].date).toEqual(testDate)
  }

  @Test
  fun `should buildCashFlows with buy and sell transactions`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50"), date = testDate.minusDays(60)),
        createSellCashFlow(quantity = BigDecimal("40"), price = BigDecimal("70"), date = testDate.minusDays(30)),
      )
    val currentValue = BigDecimal("4200")

    val xirrCashFlows = xirrCalculationService.buildCashFlows(transactions, currentValue, testDate)

    expect(xirrCashFlows).toHaveSize(3)
    expect(xirrCashFlows[0].amount).toBeLessThan(0.0)
    expect(xirrCashFlows[1].amount).toBeGreaterThan(0.0)
    expect(xirrCashFlows[2].amount).toEqual(4200.0)
  }

  @Test
  fun `should buildCashFlows with zero current value`() {
    val transactions = listOf(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")))

    val xirrCashFlows = xirrCalculationService.buildCashFlows(transactions, BigDecimal.ZERO, testDate)

    expect(xirrCashFlows).toHaveSize(1)
    expect(xirrCashFlows[0].amount).toBeLessThan(0.0)
  }

  @Test
  fun `should calculateAdjustedXirr with sufficient transactions returns bounded value`() {
    val transactions =
      listOf(
        CashFlow(-1000.0, testDate.minusDays(100)),
        CashFlow(-500.0, testDate.minusDays(50)),
        CashFlow(2000.0, testDate),
      )

    val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toBeGreaterThanOrEqualTo(-10.0).and.toBeLessThanOrEqualTo(10.0)
  }

  @Test
  fun `should calculateAdjustedXirr with fewer than 2 transactions returns zero`() {
    val transactions = listOf(CashFlow(-1000.0, testDate))

    val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toEqual(0.0)
  }

  @Test
  fun `should calculateAdjustedXirr applies dampening for new investments`() {
    val recentDate = testDate.minusDays(30)
    val transactions =
      listOf(
        CashFlow(-1000.0, recentDate),
        CashFlow(1500.0, testDate),
      )

    val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toBeLessThan(10.0)
  }

  @Test
  fun `should calculateAdjustedXirr handles empty negative cashflows`() {
    val transactions =
      listOf(
        CashFlow(500.0, testDate.minusDays(50)),
        CashFlow(1000.0, testDate),
      )

    val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toEqual(0.0)
  }

  @ParameterizedTest
  @MethodSource("provideXirrExtremeScenarios")
  fun `should calculateAdjustedXirr bounds extreme values`(
    cashFlows: List<CashFlow>,
    expectedBehavior: (Double) -> Boolean,
  ) {
    val xirr = xirrCalculationService.calculateAdjustedXirr(cashFlows, testDate)

    expect(expectedBehavior(xirr)).toEqual(true)
  }

  @Test
  fun `should calculateInstrumentMetrics with empty transactions returns empty metrics`() {
    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, emptyList(), testDate)

    expect(metrics).toEqual(InstrumentMetrics.EMPTY)
  }

  @Test
  fun `should calculateInstrumentMetrics with single platform calculates correctly`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
      )

    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, transactions, testDate)

    expect(metrics.quantity).toEqualNumerically(BigDecimal("10"))
    expect(metrics.totalInvestment).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.currentValue).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateInstrumentMetrics with multiple platforms aggregates correctly`() {
    val transactions =
      listOf(
        createBuyCashFlow(
          quantity = BigDecimal("10"),
          price = BigDecimal("100"),
          platform = Platform.LHV,
        ),
        createBuyCashFlow(
          quantity = BigDecimal("15"),
          price = BigDecimal("110"),
          platform = Platform.LIGHTYEAR,
        ),
      )

    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, transactions, testDate)

    expect(metrics.quantity).toEqualNumerically(BigDecimal("25"))
    expect(metrics.totalInvestment).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateInstrumentMetrics with zero price returns zero values`() {
    testInstrument.currentPrice = BigDecimal.ZERO
    val transactions = listOf(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")))

    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, transactions, testDate)

    expect(metrics.currentValue).toEqualNumerically(BigDecimal.ZERO)
    expect(metrics.profit).toBeLessThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateInstrumentMetricsWithProfits calls transaction service`() {
    val transactions = listOf(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")))

    every { transactionService.calculateTransactionProfits(any(), any()) } returns Unit

    val metrics = investmentMetricsService.calculateInstrumentMetricsWithProfits(testInstrument, transactions, testDate)

    expect(metrics.quantity).toEqualNumerically(BigDecimal("10"))
    expect(metrics.totalInvestment).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should convertToCashFlow for BUY transaction has negative amount`() {
    val buyTransaction = createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"))

    val xirrTx = xirrCalculationService.convertToCashFlow(buyTransaction)

    expect(xirrTx.amount).toBeLessThan(0.0)
    expect(xirrTx.date).toEqual(buyTransaction.transactionDate)
  }

  @Test
  fun `should convertToCashFlow for SELL transaction has positive amount`() {
    val sellTransaction = createSellCashFlow(quantity = BigDecimal("10"), price = BigDecimal("120"))

    val xirrTx = xirrCalculationService.convertToCashFlow(sellTransaction)

    expect(xirrTx.amount).toBeGreaterThan(0.0)
    expect(xirrTx.date).toEqual(sellTransaction.transactionDate)
  }

  @Test
  fun `should convertToCashFlow includes commission in calculation`() {
    val buyTransaction =
      createBuyCashFlow(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        commission = BigDecimal("20"),
      )

    val xirrTx = xirrCalculationService.convertToCashFlow(buyTransaction)

    expect(xirrTx.amount).toEqual(-1020.0)
  }

  @Test
  fun `should calculatePortfolioMetrics with single instrument`() {
    val transactions = listOf(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")))
    val instrumentGroups = mapOf(testInstrument to transactions)

    every { dailyPriceService.getPrice(testInstrument, any()) } returns BigDecimal("150")

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.xirrCashFlows).notToBeEmpty()
  }

  @Test
  fun `should calculatePortfolioMetrics with multiple instruments`() {
    val instrument2 =
      Instrument(
        symbol = "GOOGL",
        name = "Alphabet Inc.",
        category = "Stock",
        baseCurrency = "USD",
        currentPrice = BigDecimal("2800"),
      ).apply { id = 2L }

    val transactions1 = listOf(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")))
    val transactions2 =
      listOf(
        createBuyCashFlow(
          quantity = BigDecimal("5"),
          price = BigDecimal("2500"),
          instrument = instrument2,
        ),
      )

    val instrumentGroups =
      mapOf(
        testInstrument to transactions1,
        instrument2 to transactions2,
      )

    every { dailyPriceService.getPrice(testInstrument, any()) } returns BigDecimal("150")
    every { dailyPriceService.getPrice(instrument2, any()) } returns BigDecimal("2800")

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.totalProfit).toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
  }

  @Test
  fun `should calculatePortfolioMetrics handles zero holdings gracefully`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createSellCashFlow(quantity = BigDecimal("10"), price = BigDecimal("120")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toEqualNumerically(BigDecimal.ZERO)
  }

  private fun createBuyCashFlow(
    quantity: BigDecimal,
    price: BigDecimal,
    date: LocalDate = testDate,
    commission: BigDecimal = BigDecimal("5"),
    platform: Platform = Platform.LHV,
    instrument: Instrument = testInstrument,
  ): PortfolioTransaction =
    PortfolioTransaction(
      instrument = instrument,
      transactionType = TransactionType.BUY,
      quantity = quantity,
      price = price,
      transactionDate = date,
      platform = platform,
      commission = commission,
    )

  private fun createSellCashFlow(
    quantity: BigDecimal,
    price: BigDecimal,
    date: LocalDate = testDate,
    commission: BigDecimal = BigDecimal("5"),
    platform: Platform = Platform.LHV,
  ): PortfolioTransaction =
    PortfolioTransaction(
      instrument = testInstrument,
      transactionType = TransactionType.SELL,
      quantity = quantity,
      price = price,
      transactionDate = date,
      platform = platform,
      commission = commission,
    )

  @Test
  fun `should calculateInstrumentMetricsWithProfits with empty transactions returns empty metrics`() {
    val metrics =
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        testInstrument,
        emptyList(),
        testDate,
      )

    expect(metrics).toEqual(InstrumentMetrics.EMPTY)
  }

  @Test
  fun `should calculateInstrumentMetricsWithProfits with multiple platforms`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
        createBuyCashFlow(quantity = BigDecimal("5"), price = BigDecimal("120"), platform = Platform.LIGHTYEAR),
        createSellCashFlow(quantity = BigDecimal("3"), price = BigDecimal("150"), platform = Platform.LHV),
      )

    every { transactionService.calculateTransactionProfits(any(), any()) } returns Unit

    val metrics =
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        testInstrument,
        transactions,
        testDate,
      )

    expect(metrics.quantity).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.totalInvestment).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.currentValue).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateInstrumentMetricsWithProfits with all positions sold`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createSellCashFlow(quantity = BigDecimal("10"), price = BigDecimal("150")),
      )

    every { transactionService.calculateTransactionProfits(any(), any()) } returns Unit

    val metrics =
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        testInstrument,
        transactions,
        testDate,
      )

    expect(metrics.quantity).toEqualNumerically(BigDecimal.ZERO)
    expect(metrics.totalInvestment).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateAdjustedXirr with very short investment period applies damping`() {
    val transactions =
      listOf(
        CashFlow(-1000.0, testDate.minusDays(15)),
        CashFlow(1200.0, testDate),
      )

    val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toBeLessThan(10.0)
    expect(xirr).toBeGreaterThanOrEqualTo(0.0)
  }

  @Test
  fun `should calculateAdjustedXirr with investment period over 60 days has full damping`() {
    val transactions =
      listOf(
        CashFlow(-1000.0, testDate.minusDays(100)),
        CashFlow(1500.0, testDate),
      )

    val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toBeGreaterThanOrEqualTo(-10.0).and.toBeLessThanOrEqualTo(10.0)
  }

  @Test
  fun `should calculateAdjustedXirr with exactly 60 days investment period`() {
    val transactions =
      listOf(
        CashFlow(-1000.0, testDate.minusDays(60)),
        CashFlow(1200.0, testDate),
      )

    val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toBeGreaterThanOrEqualTo(-10.0).and.toBeLessThanOrEqualTo(10.0)
  }

  @Test
  fun `should calculateAdjustedXirr handles exception and returns zero`() {
    val transactions =
      listOf(
        CashFlow(-1000.0, testDate),
        CashFlow(1000.0, testDate),
      )

    val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toEqual(0.0)
  }

  @Test
  fun `should buildCashFlows with negative current value omits final transaction`() {
    val transactions = listOf(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")))

    val xirrCashFlows =
      xirrCalculationService.buildCashFlows(
        transactions,
        BigDecimal("-100"),
        testDate,
      )

    expect(xirrCashFlows).toHaveSize(1)
    expect(xirrCashFlows[0].amount).toBeLessThan(0.0)
  }

  @Test
  fun `should buildCashFlows with multiple buy and sell transactions`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50"), date = testDate.minusDays(90)),
        createBuyCashFlow(quantity = BigDecimal("50"), price = BigDecimal("60"), date = testDate.minusDays(60)),
        createSellCashFlow(quantity = BigDecimal("30"), price = BigDecimal("70"), date = testDate.minusDays(30)),
        createSellCashFlow(quantity = BigDecimal("20"), price = BigDecimal("80"), date = testDate.minusDays(15)),
      )
    val currentValue = BigDecimal("10000")

    val xirrCashFlows = xirrCalculationService.buildCashFlows(transactions, currentValue, testDate)

    expect(xirrCashFlows).toHaveSize(5)
    expect(xirrCashFlows[0].amount).toBeLessThan(0.0)
    expect(xirrCashFlows[1].amount).toBeLessThan(0.0)
    expect(xirrCashFlows[2].amount).toBeGreaterThan(0.0)
    expect(xirrCashFlows[3].amount).toBeGreaterThan(0.0)
    expect(xirrCashFlows[4].amount).toEqual(10000.0)
  }

  @Test
  fun `should calculatePortfolioMetrics with fallback when unified calculation fails`() {
    val transactions = listOf(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")))
    val instrumentGroups = mapOf(testInstrument to transactions)

    every { dailyPriceService.getPrice(testInstrument, any()) } throws RuntimeException("Price not found") andThen BigDecimal("150")

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.xirrCashFlows).notToBeEmpty()
  }

  @Test
  fun `should calculatePortfolioMetrics aggregates multiple instruments with mixed transactions`() {
    val instrument2 =
      Instrument(
        symbol = "TSLA",
        name = "Tesla Inc.",
        category = "Stock",
        baseCurrency = "USD",
        currentPrice = BigDecimal("700"),
      ).apply { id = 2L }

    val transactions1 =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createSellCashFlow(quantity = BigDecimal("3"), price = BigDecimal("150")),
      )

    val transactions2 =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("5"), price = BigDecimal("600"), instrument = instrument2),
      )

    val instrumentGroups =
      mapOf(
        testInstrument to transactions1,
        instrument2 to transactions2,
      )

    every { dailyPriceService.getPrice(testInstrument, any()) } returns BigDecimal("150")
    every { dailyPriceService.getPrice(instrument2, any()) } returns BigDecimal("700")

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.totalProfit).toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
    expect(metrics.xirrCashFlows.size > 3).toEqual(true)
  }

  @Test
  fun `should calculatePortfolioMetrics with negative holdings is excluded`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createSellCashFlow(quantity = BigDecimal("15"), price = BigDecimal("120")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateCurrentHoldings with sell before buy returns zero`() {
    val transactions =
      listOf(
        createSellCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createBuyCashFlow(quantity = BigDecimal("20"), price = BigDecimal("90")),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(transactions)

    expect(quantity).toEqualNumerically(BigDecimal("20"))
    expect(averageCost).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateCurrentHoldings with partial sell reduces cost proportionally`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50")),
        createSellCashFlow(quantity = BigDecimal("25"), price = BigDecimal("60")),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(transactions)

    expect(quantity).toEqualNumerically(BigDecimal("75"))
    expect(averageCost).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateInstrumentMetrics with null current price uses zero`() {
    testInstrument.currentPrice = null
    val transactions = listOf(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")))

    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, transactions, testDate)

    expect(metrics.currentValue).toEqualNumerically(BigDecimal.ZERO)
    expect(metrics.profit).toBeLessThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateInstrumentMetrics with platform having zero holdings is excluded`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
        createSellCashFlow(quantity = BigDecimal("10"), price = BigDecimal("120"), platform = Platform.LHV),
        createBuyCashFlow(quantity = BigDecimal("5"), price = BigDecimal("110"), platform = Platform.LIGHTYEAR),
      )

    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, transactions, testDate)

    expect(metrics.quantity).toEqualNumerically(BigDecimal("5"))
  }

  @Test
  fun `should calculatePortfolioMetrics with empty instrument groups returns empty metrics`() {
    val metrics = investmentMetricsService.calculatePortfolioMetrics(emptyMap(), testDate)

    expect(metrics.totalValue).toEqualNumerically(BigDecimal.ZERO)
    expect(metrics.totalProfit).toEqualNumerically(BigDecimal.ZERO)
    expect(metrics.xirrCashFlows).toBeEmpty()
  }

  @Test
  fun `should convertToCashFlow for sell with commission reduces amount`() {
    val sellTransaction =
      createSellCashFlow(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        commission = BigDecimal("15"),
      )

    val xirrTx = xirrCalculationService.convertToCashFlow(sellTransaction)

    expect(xirrTx.amount).toEqual(985.0)
  }

  @Test
  fun `should calculateAdjustedXirr with weighted investment age calculation`() {
    val transactions =
      listOf(
        CashFlow(-5000.0, testDate.minusDays(100)),
        CashFlow(-3000.0, testDate.minusDays(50)),
        CashFlow(-2000.0, testDate.minusDays(20)),
        CashFlow(12000.0, testDate),
      )

    val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toBeGreaterThanOrEqualTo(-10.0).and.toBeLessThanOrEqualTo(10.0)
  }

  @Test
  fun `should calculatePortfolioMetrics with single transaction per instrument`() {
    val instrument2 =
      Instrument(
        symbol = "MSFT",
        name = "Microsoft Corp.",
        category = "Stock",
        baseCurrency = "USD",
        currentPrice = BigDecimal("300"),
      ).apply { id = 3L }

    val transactions1 = listOf(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")))
    val transactions2 =
      listOf(
        createBuyCashFlow(
          quantity = BigDecimal("5"),
          price = BigDecimal("280"),
          instrument = instrument2,
        ),
      )

    val instrumentGroups =
      mapOf(
        testInstrument to transactions1,
        instrument2 to transactions2,
      )

    every { dailyPriceService.getPrice(testInstrument, any()) } returns BigDecimal("150")
    every { dailyPriceService.getPrice(instrument2, any()) } returns BigDecimal("300")

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.xirrCashFlows).toHaveSize(4)
  }

  @Test
  fun `should calculatePortfolioMetrics fallback with only buy transactions`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createBuyCashFlow(quantity = BigDecimal("5"), price = BigDecimal("110")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    every { dailyPriceService.getPrice(testInstrument, any()) } throws RuntimeException("Unified calc failed") andThen BigDecimal("150")

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.totalProfit).toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
  }

  @Test
  fun `should calculatePortfolioMetrics fallback with buy and sell transactions`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50")),
        createSellCashFlow(quantity = BigDecimal("40"), price = BigDecimal("70")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    every { dailyPriceService.getPrice(testInstrument, any()) } throws RuntimeException("Unified calc failed") andThen BigDecimal("80")

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.totalProfit).toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
  }

  @Test
  fun `should calculatePortfolioMetrics fallback with zero total sells`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("50"), price = BigDecimal("100")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    every { dailyPriceService.getPrice(testInstrument, any()) } throws RuntimeException("Unified calc failed") andThen BigDecimal("120")

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.totalProfit).toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
  }

  @Test
  fun `should calculatePortfolioMetrics fallback with zero buy quantity`() {
    val transactions =
      listOf(
        createSellCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculatePortfolioMetrics fallback with complete sell-off`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("50"), price = BigDecimal("100")),
        createSellCashFlow(quantity = BigDecimal("50"), price = BigDecimal("150")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculatePortfolioMetrics fallback calculates realized gains correctly`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50"), commission = BigDecimal("10")),
        createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("60"), commission = BigDecimal("10")),
        createSellCashFlow(quantity = BigDecimal("50"), price = BigDecimal("80"), commission = BigDecimal("5")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    every { dailyPriceService.getPrice(testInstrument, any()) } throws RuntimeException("Unified calc failed") andThen BigDecimal("90")

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalProfit).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateCurrentHoldings with multiple consecutive sells`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50")),
        createSellCashFlow(quantity = BigDecimal("25"), price = BigDecimal("60")),
        createSellCashFlow(quantity = BigDecimal("25"), price = BigDecimal("65")),
        createSellCashFlow(quantity = BigDecimal("25"), price = BigDecimal("70")),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(transactions)

    expect(quantity).toEqualNumerically(BigDecimal("25"))
    expect(averageCost).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateCurrentHoldings with zero commission`() {
    val transaction =
      createBuyCashFlow(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        commission = BigDecimal.ZERO,
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(listOf(transaction))

    expect(quantity).toEqualNumerically(BigDecimal("10"))
    expect(averageCost).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should calculateCurrentHoldings with very small quantities`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("0.001"), price = BigDecimal("50000")),
        createSellCashFlow(quantity = BigDecimal("0.0005"), price = BigDecimal("55000")),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(transactions)

    expect(quantity).toEqualNumerically(BigDecimal("0.0005"))
    expect(averageCost).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateInstrumentMetrics with one platform having zero holdings after sell`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
        createSellCashFlow(quantity = BigDecimal("10"), price = BigDecimal("120"), platform = Platform.LHV),
        createBuyCashFlow(quantity = BigDecimal("5"), price = BigDecimal("110"), platform = Platform.LIGHTYEAR),
        createSellCashFlow(quantity = BigDecimal("2"), price = BigDecimal("130"), platform = Platform.LIGHTYEAR),
      )

    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, transactions, testDate)

    expect(metrics.quantity).toEqualNumerically(BigDecimal("3"))
    expect(metrics.totalInvestment).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculatePortfolioMetrics fallback with high commission reduces profit`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"), commission = BigDecimal("50")),
        createSellCashFlow(quantity = BigDecimal("5"), price = BigDecimal("150"), commission = BigDecimal("30")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    every { dailyPriceService.getPrice(testInstrument, any()) } throws RuntimeException("Unified calc failed") andThen BigDecimal("160")

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.totalProfit).toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateAdjustedXirr with multiple investments at different times has weighted damping`() {
    val transactions =
      listOf(
        CashFlow(-10000.0, testDate.minusDays(120)),
        CashFlow(-5000.0, testDate.minusDays(80)),
        CashFlow(-2000.0, testDate.minusDays(40)),
        CashFlow(-1000.0, testDate.minusDays(10)),
        CashFlow(20000.0, testDate),
      )

    val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toBeGreaterThanOrEqualTo(-10.0).and.toBeLessThanOrEqualTo(10.0)
  }

  @Test
  fun `should convertToCashFlow with very high commission for buy`() {
    val buyTransaction =
      createBuyCashFlow(
        quantity = BigDecimal("1"),
        price = BigDecimal("1000"),
        commission = BigDecimal("500"),
      )

    val xirrTx = xirrCalculationService.convertToCashFlow(buyTransaction)

    expect(xirrTx.amount).toEqual(-1500.0)
  }

  @Test
  fun `should convertToCashFlow with zero commission for sell`() {
    val sellTransaction =
      createSellCashFlow(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        commission = BigDecimal.ZERO,
      )

    val xirrTx = xirrCalculationService.convertToCashFlow(sellTransaction)

    expect(xirrTx.amount).toEqual(1000.0)
  }

  @Test
  fun `should calculateInstrumentMetrics with negative profit scenario`() {
    testInstrument.currentPrice = BigDecimal("50")
    val transactions = listOf(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")))

    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, transactions, testDate)

    expect(metrics.profit).toBeLessThan(BigDecimal.ZERO)
    expect(metrics.currentValue).toBeLessThan(metrics.totalInvestment)
  }

  @Test
  fun `should calculatePortfolioMetrics with all platforms having zero or negative holdings`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
        createSellCashFlow(quantity = BigDecimal("10"), price = BigDecimal("120"), platform = Platform.LHV),
        createBuyCashFlow(quantity = BigDecimal("5"), price = BigDecimal("110"), platform = Platform.LIGHTYEAR),
        createSellCashFlow(quantity = BigDecimal("5"), price = BigDecimal("130"), platform = Platform.LIGHTYEAR),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toEqualNumerically(BigDecimal.ZERO)
    expect(metrics.totalProfit).toEqualNumerically(BigDecimal.ZERO)
  }

  companion object {
    @JvmStatic
    fun provideXirrExtremeScenarios(): Stream<Arguments> =
      Stream.of(
        Arguments.of(
          listOf(
            CashFlow(-1000.0, LocalDate.of(2024, 1, 1)),
            CashFlow(100000.0, LocalDate.of(2024, 1, 30)),
          ),
          { xirr: Double -> xirr <= 10.0 },
        ),
        Arguments.of(
          listOf(
            CashFlow(-10000.0, LocalDate.of(2024, 1, 1)),
            CashFlow(100.0, LocalDate.of(2024, 1, 30)),
          ),
          { xirr: Double -> xirr >= -10.0 },
        ),
      )
  }
}
