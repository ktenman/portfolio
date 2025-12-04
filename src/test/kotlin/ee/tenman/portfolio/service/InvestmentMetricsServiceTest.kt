package ee.tenman.portfolio.service

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
import ee.tenman.portfolio.service.xirr.Transaction
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
import java.time.LocalDate
import java.util.stream.Stream

class InvestmentMetricsServiceTest {
  private val dailyPriceService = mockk<DailyPriceService>()
  private val transactionService = mockk<TransactionService>()
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
    investmentMetricsService = InvestmentMetricsService(dailyPriceService, transactionService, Clock.systemDefaultZone())

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
    val transaction = createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"))

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(listOf(transaction))

    expect(quantity).toEqualNumerically(BigDecimal("10"))
    expect(averageCost).toEqualNumerically(BigDecimal("100.50"))
  }

  @Test
  fun `should calculateCurrentHoldings with multiple buy transactions`() {
    val transactions =
      listOf(
        createBuyTransaction(
          quantity = BigDecimal("10"),
          price = BigDecimal("100"),
          date = testDate.minusDays(10),
        ),
        createBuyTransaction(
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
        .add(BigDecimal("5"))
      .add(BigDecimal("5").multiply(BigDecimal("120")).add(BigDecimal("5")))
    val expectedAvgCost = expectedTotalCost.divide(BigDecimal("15"), 10, RoundingMode.HALF_UP)
    expect(averageCost).toEqualNumerically(expectedAvgCost)
  }

  @Test
  fun `should calculateCurrentHoldings with buy and sell transactions`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50")),
        createSellTransaction(quantity = BigDecimal("40"), price = BigDecimal("60")),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(transactions)

    expect(quantity).toEqualNumerically(BigDecimal("60"))
    expect(averageCost).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateCurrentHoldings with complete sell-off`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("50"), price = BigDecimal("100")),
        createSellTransaction(quantity = BigDecimal("50"), price = BigDecimal("120")),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(transactions)

    expect(quantity).toEqualNumerically(BigDecimal.ZERO)
    expect(averageCost).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateCurrentHoldings handles commission correctly`() {
    val transaction =
      createBuyTransaction(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        commission = BigDecimal("10"),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(listOf(transaction))

    expect(quantity).toEqualNumerically(BigDecimal("10"))
    expect(averageCost).toEqualNumerically(BigDecimal("101"))
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
  fun `should buildXirrTransactions with buy transactions only`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"), date = testDate.minusDays(30)),
        createBuyTransaction(quantity = BigDecimal("5"), price = BigDecimal("110"), date = testDate.minusDays(15)),
      )
    val currentValue = BigDecimal("1500")

    val xirrTransactions = investmentMetricsService.buildXirrTransactions(transactions, currentValue, testDate)

    expect(xirrTransactions).toHaveSize(3)
    expect(xirrTransactions[0].amount).toBeLessThan(0.0)
    expect(xirrTransactions[1].amount).toBeLessThan(0.0)
    expect(xirrTransactions[2].amount).toEqual(1500.0)
    expect(xirrTransactions[2].date).toEqual(testDate)
  }

  @Test
  fun `should buildXirrTransactions with buy and sell transactions`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"), date = testDate.minusDays(60)),
        createSellTransaction(quantity = BigDecimal("40"), price = BigDecimal("70"), date = testDate.minusDays(30)),
      )
    val currentValue = BigDecimal("4200")

    val xirrTransactions = investmentMetricsService.buildXirrTransactions(transactions, currentValue, testDate)

    expect(xirrTransactions).toHaveSize(3)
    expect(xirrTransactions[0].amount).toBeLessThan(0.0)
    expect(xirrTransactions[1].amount).toBeGreaterThan(0.0)
    expect(xirrTransactions[2].amount).toEqual(4200.0)
  }

  @Test
  fun `should buildXirrTransactions with zero current value`() {
    val transactions = listOf(createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")))

    val xirrTransactions = investmentMetricsService.buildXirrTransactions(transactions, BigDecimal.ZERO, testDate)

    expect(xirrTransactions).toHaveSize(1)
    expect(xirrTransactions[0].amount).toBeLessThan(0.0)
  }

  @Test
  fun `should calculateAdjustedXirr with sufficient transactions returns bounded value`() {
    val transactions =
      listOf(
        Transaction(-1000.0, testDate.minusDays(100)),
        Transaction(-500.0, testDate.minusDays(50)),
        Transaction(2000.0, testDate),
      )

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toBeGreaterThanOrEqualTo(-10.0).and.toBeLessThanOrEqualTo(10.0)
  }

  @Test
  fun `should calculateAdjustedXirr with fewer than 2 transactions returns zero`() {
    val transactions = listOf(Transaction(-1000.0, testDate))

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toEqual(0.0)
  }

  @Test
  fun `should calculateAdjustedXirr applies dampening for new investments`() {
    val recentDate = testDate.minusDays(30)
    val transactions =
      listOf(
        Transaction(-1000.0, recentDate),
        Transaction(1500.0, testDate),
      )

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toBeLessThan(10.0)
  }

  @Test
  fun `should calculateAdjustedXirr handles empty negative cashflows`() {
    val transactions =
      listOf(
        Transaction(500.0, testDate.minusDays(50)),
        Transaction(1000.0, testDate),
      )

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toEqual(0.0)
  }

  @ParameterizedTest
  @MethodSource("provideXirrExtremeScenarios")
  fun `should calculateAdjustedXirr bounds extreme values`(
    transactions: List<Transaction>,
    expectedBehavior: (Double) -> Boolean,
  ) {
    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, testDate)

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
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
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
        createBuyTransaction(
          quantity = BigDecimal("10"),
          price = BigDecimal("100"),
          platform = Platform.LHV,
        ),
        createBuyTransaction(
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
    val transactions = listOf(createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")))

    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, transactions, testDate)

    expect(metrics.currentValue).toEqualNumerically(BigDecimal.ZERO)
    expect(metrics.profit).toBeLessThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateInstrumentMetricsWithProfits calls transaction service`() {
    val transactions = listOf(createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")))

    every { transactionService.calculateTransactionProfits(any(), any()) } returns Unit

    val metrics = investmentMetricsService.calculateInstrumentMetricsWithProfits(testInstrument, transactions, testDate)

    expect(metrics.quantity).toEqualNumerically(BigDecimal("10"))
    expect(metrics.totalInvestment).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should convertToXirrTransaction for BUY transaction has negative amount`() {
    val buyTransaction = createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"))

    val xirrTx = investmentMetricsService.convertToXirrTransaction(buyTransaction)

    expect(xirrTx.amount).toBeLessThan(0.0)
    expect(xirrTx.date).toEqual(buyTransaction.transactionDate)
  }

  @Test
  fun `should convertToXirrTransaction for SELL transaction has positive amount`() {
    val sellTransaction = createSellTransaction(quantity = BigDecimal("10"), price = BigDecimal("120"))

    val xirrTx = investmentMetricsService.convertToXirrTransaction(sellTransaction)

    expect(xirrTx.amount).toBeGreaterThan(0.0)
    expect(xirrTx.date).toEqual(sellTransaction.transactionDate)
  }

  @Test
  fun `should convertToXirrTransaction includes commission in calculation`() {
    val buyTransaction =
      createBuyTransaction(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        commission = BigDecimal("20"),
      )

    val xirrTx = investmentMetricsService.convertToXirrTransaction(buyTransaction)

    expect(xirrTx.amount).toEqual(-1020.0)
  }

  @Test
  fun `should calculatePortfolioMetrics with single instrument`() {
    val transactions = listOf(createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")))
    val instrumentGroups = mapOf(testInstrument to transactions)

    every { dailyPriceService.getPrice(testInstrument, any()) } returns BigDecimal("150")

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.xirrTransactions).notToBeEmpty()
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

    val transactions1 = listOf(createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")))
    val transactions2 =
      listOf(
        createBuyTransaction(
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
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createSellTransaction(quantity = BigDecimal("10"), price = BigDecimal("120")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toEqualNumerically(BigDecimal.ZERO)
  }

  private fun createBuyTransaction(
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

  private fun createSellTransaction(
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
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
        createBuyTransaction(quantity = BigDecimal("5"), price = BigDecimal("120"), platform = Platform.LIGHTYEAR),
        createSellTransaction(quantity = BigDecimal("3"), price = BigDecimal("150"), platform = Platform.LHV),
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
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createSellTransaction(quantity = BigDecimal("10"), price = BigDecimal("150")),
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
        Transaction(-1000.0, testDate.minusDays(15)),
        Transaction(1200.0, testDate),
      )

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toBeLessThan(10.0)
    expect(xirr).toBeGreaterThanOrEqualTo(0.0)
  }

  @Test
  fun `should calculateAdjustedXirr with investment period over 60 days has full damping`() {
    val transactions =
      listOf(
        Transaction(-1000.0, testDate.minusDays(100)),
        Transaction(1500.0, testDate),
      )

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toBeGreaterThanOrEqualTo(-10.0).and.toBeLessThanOrEqualTo(10.0)
  }

  @Test
  fun `should calculateAdjustedXirr with exactly 60 days investment period`() {
    val transactions =
      listOf(
        Transaction(-1000.0, testDate.minusDays(60)),
        Transaction(1200.0, testDate),
      )

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toBeGreaterThanOrEqualTo(-10.0).and.toBeLessThanOrEqualTo(10.0)
  }

  @Test
  fun `should calculateAdjustedXirr handles exception and returns zero`() {
    val transactions =
      listOf(
        Transaction(-1000.0, testDate),
        Transaction(1000.0, testDate),
      )

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toEqual(0.0)
  }

  @Test
  fun `should buildXirrTransactions with negative current value omits final transaction`() {
    val transactions = listOf(createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")))

    val xirrTransactions =
      investmentMetricsService.buildXirrTransactions(
        transactions,
        BigDecimal("-100"),
        testDate,
      )

    expect(xirrTransactions).toHaveSize(1)
    expect(xirrTransactions[0].amount).toBeLessThan(0.0)
  }

  @Test
  fun `should buildXirrTransactions with multiple buy and sell transactions`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"), date = testDate.minusDays(90)),
        createBuyTransaction(quantity = BigDecimal("50"), price = BigDecimal("60"), date = testDate.minusDays(60)),
        createSellTransaction(quantity = BigDecimal("30"), price = BigDecimal("70"), date = testDate.minusDays(30)),
        createSellTransaction(quantity = BigDecimal("20"), price = BigDecimal("80"), date = testDate.minusDays(15)),
      )
    val currentValue = BigDecimal("10000")

    val xirrTransactions = investmentMetricsService.buildXirrTransactions(transactions, currentValue, testDate)

    expect(xirrTransactions).toHaveSize(5)
    expect(xirrTransactions[0].amount).toBeLessThan(0.0)
    expect(xirrTransactions[1].amount).toBeLessThan(0.0)
    expect(xirrTransactions[2].amount).toBeGreaterThan(0.0)
    expect(xirrTransactions[3].amount).toBeGreaterThan(0.0)
    expect(xirrTransactions[4].amount).toEqual(10000.0)
  }

  @Test
  fun `should calculatePortfolioMetrics with fallback when unified calculation fails`() {
    val transactions = listOf(createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")))
    val instrumentGroups = mapOf(testInstrument to transactions)

    every { dailyPriceService.getPrice(testInstrument, any()) } throws RuntimeException("Price not found") andThen BigDecimal("150")

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toBeGreaterThan(BigDecimal.ZERO)
    expect(metrics.xirrTransactions).notToBeEmpty()
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
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createSellTransaction(quantity = BigDecimal("3"), price = BigDecimal("150")),
      )

    val transactions2 =
      listOf(
        createBuyTransaction(quantity = BigDecimal("5"), price = BigDecimal("600"), instrument = instrument2),
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
    expect(metrics.xirrTransactions.size > 3).toEqual(true)
  }

  @Test
  fun `should calculatePortfolioMetrics with negative holdings is excluded`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createSellTransaction(quantity = BigDecimal("15"), price = BigDecimal("120")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateCurrentHoldings with sell before buy returns zero`() {
    val transactions =
      listOf(
        createSellTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createBuyTransaction(quantity = BigDecimal("20"), price = BigDecimal("90")),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(transactions)

    expect(quantity).toEqualNumerically(BigDecimal("20"))
    expect(averageCost).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateCurrentHoldings with partial sell reduces cost proportionally`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50")),
        createSellTransaction(quantity = BigDecimal("25"), price = BigDecimal("60")),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(transactions)

    expect(quantity).toEqualNumerically(BigDecimal("75"))
    expect(averageCost).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateInstrumentMetrics with null current price uses zero`() {
    testInstrument.currentPrice = null
    val transactions = listOf(createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")))

    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, transactions, testDate)

    expect(metrics.currentValue).toEqualNumerically(BigDecimal.ZERO)
    expect(metrics.profit).toBeLessThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateInstrumentMetrics with platform having zero holdings is excluded`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
        createSellTransaction(quantity = BigDecimal("10"), price = BigDecimal("120"), platform = Platform.LHV),
        createBuyTransaction(quantity = BigDecimal("5"), price = BigDecimal("110"), platform = Platform.LIGHTYEAR),
      )

    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, transactions, testDate)

    expect(metrics.quantity).toEqualNumerically(BigDecimal("5"))
  }

  @Test
  fun `should calculatePortfolioMetrics with empty instrument groups returns empty metrics`() {
    val metrics = investmentMetricsService.calculatePortfolioMetrics(emptyMap(), testDate)

    expect(metrics.totalValue).toEqualNumerically(BigDecimal.ZERO)
    expect(metrics.totalProfit).toEqualNumerically(BigDecimal.ZERO)
    expect(metrics.xirrTransactions).toBeEmpty()
  }

  @Test
  fun `should convertToXirrTransaction for sell with commission reduces amount`() {
    val sellTransaction =
      createSellTransaction(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        commission = BigDecimal("15"),
      )

    val xirrTx = investmentMetricsService.convertToXirrTransaction(sellTransaction)

    expect(xirrTx.amount).toEqual(985.0)
  }

  @Test
  fun `should calculateAdjustedXirr with weighted investment age calculation`() {
    val transactions =
      listOf(
        Transaction(-5000.0, testDate.minusDays(100)),
        Transaction(-3000.0, testDate.minusDays(50)),
        Transaction(-2000.0, testDate.minusDays(20)),
        Transaction(12000.0, testDate),
      )

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, testDate)

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

    val transactions1 = listOf(createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")))
    val transactions2 =
      listOf(
        createBuyTransaction(
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
    expect(metrics.xirrTransactions).toHaveSize(4)
  }

  @Test
  fun `should calculateNetQuantity with only buy transactions`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createBuyTransaction(quantity = BigDecimal("15"), price = BigDecimal("110")),
      )

    val netQuantity =
      transactions.fold(BigDecimal.ZERO) { acc, tx ->
      when (tx.transactionType) {
        TransactionType.BUY -> acc.add(tx.quantity)
        TransactionType.SELL -> acc.subtract(tx.quantity)
      }
    }

    expect(netQuantity).toEqualNumerically(BigDecimal("25"))
  }

  @Test
  fun `should calculateNetQuantity with only sell transactions`() {
    val transactions =
      listOf(
        createSellTransaction(quantity = BigDecimal("5"), price = BigDecimal("120")),
        createSellTransaction(quantity = BigDecimal("3"), price = BigDecimal("125")),
      )

    val netQuantity =
      transactions.fold(BigDecimal.ZERO) { acc, tx ->
      when (tx.transactionType) {
        TransactionType.BUY -> acc.add(tx.quantity)
        TransactionType.SELL -> acc.subtract(tx.quantity)
      }
    }

    expect(netQuantity).toEqualNumerically(BigDecimal("-8"))
  }

  @Test
  fun `should calculateNetQuantity with mixed buy and sell transactions`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50")),
        createSellTransaction(quantity = BigDecimal("30"), price = BigDecimal("60")),
        createBuyTransaction(quantity = BigDecimal("20"), price = BigDecimal("55")),
        createSellTransaction(quantity = BigDecimal("40"), price = BigDecimal("65")),
      )

    val netQuantity =
      transactions.fold(BigDecimal.ZERO) { acc, tx ->
      when (tx.transactionType) {
        TransactionType.BUY -> acc.add(tx.quantity)
        TransactionType.SELL -> acc.subtract(tx.quantity)
      }
    }

    expect(netQuantity).toEqualNumerically(BigDecimal("50"))
  }

  @Test
  fun `should calculateNetQuantity with empty transactions`() {
    val netQuantity =
      emptyList<PortfolioTransaction>().fold(BigDecimal.ZERO) { acc, tx ->
      when (tx.transactionType) {
        TransactionType.BUY -> acc.add(tx.quantity)
        TransactionType.SELL -> acc.subtract(tx.quantity)
      }
    }

    expect(netQuantity).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculatePortfolioMetrics fallback with only buy transactions`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createBuyTransaction(quantity = BigDecimal("5"), price = BigDecimal("110")),
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
        createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50")),
        createSellTransaction(quantity = BigDecimal("40"), price = BigDecimal("70")),
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
        createBuyTransaction(quantity = BigDecimal("50"), price = BigDecimal("100")),
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
        createSellTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculatePortfolioMetrics fallback with complete sell-off`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("50"), price = BigDecimal("100")),
        createSellTransaction(quantity = BigDecimal("50"), price = BigDecimal("150")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    expect(metrics.totalValue).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculatePortfolioMetrics fallback calculates realized gains correctly`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"), commission = BigDecimal("10")),
        createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("60"), commission = BigDecimal("10")),
        createSellTransaction(quantity = BigDecimal("50"), price = BigDecimal("80"), commission = BigDecimal("5")),
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
        createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50")),
        createSellTransaction(quantity = BigDecimal("25"), price = BigDecimal("60")),
        createSellTransaction(quantity = BigDecimal("25"), price = BigDecimal("65")),
        createSellTransaction(quantity = BigDecimal("25"), price = BigDecimal("70")),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(transactions)

    expect(quantity).toEqualNumerically(BigDecimal("25"))
    expect(averageCost).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateCurrentHoldings with zero commission`() {
    val transaction =
      createBuyTransaction(
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
        createBuyTransaction(quantity = BigDecimal("0.001"), price = BigDecimal("50000")),
        createSellTransaction(quantity = BigDecimal("0.0005"), price = BigDecimal("55000")),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(transactions)

    expect(quantity).toEqualNumerically(BigDecimal("0.0005"))
    expect(averageCost).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateInstrumentMetrics with one platform having zero holdings after sell`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
        createSellTransaction(quantity = BigDecimal("10"), price = BigDecimal("120"), platform = Platform.LHV),
        createBuyTransaction(quantity = BigDecimal("5"), price = BigDecimal("110"), platform = Platform.LIGHTYEAR),
        createSellTransaction(quantity = BigDecimal("2"), price = BigDecimal("130"), platform = Platform.LIGHTYEAR),
      )

    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, transactions, testDate)

    expect(metrics.quantity).toEqualNumerically(BigDecimal("3"))
    expect(metrics.totalInvestment).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculatePortfolioMetrics fallback with high commission reduces profit`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"), commission = BigDecimal("50")),
        createSellTransaction(quantity = BigDecimal("5"), price = BigDecimal("150"), commission = BigDecimal("30")),
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
        Transaction(-10000.0, testDate.minusDays(120)),
        Transaction(-5000.0, testDate.minusDays(80)),
        Transaction(-2000.0, testDate.minusDays(40)),
        Transaction(-1000.0, testDate.minusDays(10)),
        Transaction(20000.0, testDate),
      )

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toBeGreaterThanOrEqualTo(-10.0).and.toBeLessThanOrEqualTo(10.0)
  }

  @Test
  fun `should convertToXirrTransaction with very high commission for buy`() {
    val buyTransaction =
      createBuyTransaction(
        quantity = BigDecimal("1"),
        price = BigDecimal("1000"),
        commission = BigDecimal("500"),
      )

    val xirrTx = investmentMetricsService.convertToXirrTransaction(buyTransaction)

    expect(xirrTx.amount).toEqual(-1500.0)
  }

  @Test
  fun `should convertToXirrTransaction with zero commission for sell`() {
    val sellTransaction =
      createSellTransaction(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        commission = BigDecimal.ZERO,
      )

    val xirrTx = investmentMetricsService.convertToXirrTransaction(sellTransaction)

    expect(xirrTx.amount).toEqual(1000.0)
  }

  @Test
  fun `should calculateInstrumentMetrics with negative profit scenario`() {
    testInstrument.currentPrice = BigDecimal("50")
    val transactions = listOf(createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")))

    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, transactions, testDate)

    expect(metrics.profit).toBeLessThan(BigDecimal.ZERO)
    expect(metrics.currentValue).toBeLessThan(metrics.totalInvestment)
  }

  @Test
  fun `should calculatePortfolioMetrics with all platforms having zero or negative holdings`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
        createSellTransaction(quantity = BigDecimal("10"), price = BigDecimal("120"), platform = Platform.LHV),
        createBuyTransaction(quantity = BigDecimal("5"), price = BigDecimal("110"), platform = Platform.LIGHTYEAR),
        createSellTransaction(quantity = BigDecimal("5"), price = BigDecimal("130"), platform = Platform.LIGHTYEAR),
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
            Transaction(-1000.0, LocalDate.of(2024, 1, 1)),
            Transaction(100000.0, LocalDate.of(2024, 1, 30)),
          ),
          { xirr: Double -> xirr <= 10.0 },
        ),
        Arguments.of(
          listOf(
            Transaction(-10000.0, LocalDate.of(2024, 1, 1)),
            Transaction(100.0, LocalDate.of(2024, 1, 30)),
          ),
          { xirr: Double -> xirr >= -10.0 },
        ),
      )
  }
}
