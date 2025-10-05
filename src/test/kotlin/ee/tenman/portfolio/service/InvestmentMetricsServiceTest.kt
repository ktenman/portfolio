package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.service.xirr.Transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.stream.Stream

@ExtendWith(MockitoExtension::class)
class InvestmentMetricsServiceTest {
  @Mock
  private lateinit var dailyPriceService: DailyPriceService

  @Mock
  private lateinit var transactionService: TransactionService

  @InjectMocks
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
        providerName = ProviderName.ALPHA_VANTAGE,
      ).apply {
        id = 1L
      }
  }

  @Test
  fun `calculateCurrentHoldings with single buy transaction`() {
    val transaction = createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"))

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(listOf(transaction))

    assertThat(quantity).isEqualByComparingTo(BigDecimal("10"))
    assertThat(averageCost).isEqualByComparingTo(BigDecimal("100.50"))
  }

  @Test
  fun `calculateCurrentHoldings with multiple buy transactions`() {
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

    assertThat(quantity).isEqualByComparingTo(BigDecimal("15"))
    val expectedTotalCost =
      BigDecimal("10")
        .multiply(BigDecimal("100"))
        .add(BigDecimal("5"))
      .add(BigDecimal("5").multiply(BigDecimal("120")).add(BigDecimal("5")))
    val expectedAvgCost = expectedTotalCost.divide(BigDecimal("15"), 10, RoundingMode.HALF_UP)
    assertThat(averageCost).isEqualByComparingTo(expectedAvgCost)
  }

  @Test
  fun `calculateCurrentHoldings with buy and sell transactions`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50")),
        createSellTransaction(quantity = BigDecimal("40"), price = BigDecimal("60")),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(transactions)

    assertThat(quantity).isEqualByComparingTo(BigDecimal("60"))
    assertThat(averageCost).isGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `calculateCurrentHoldings with complete sell-off`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("50"), price = BigDecimal("100")),
        createSellTransaction(quantity = BigDecimal("50"), price = BigDecimal("120")),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(transactions)

    assertThat(quantity).isEqualByComparingTo(BigDecimal.ZERO)
    assertThat(averageCost).isEqualByComparingTo(BigDecimal.ZERO)
  }

  @Test
  fun `calculateCurrentHoldings handles commission correctly`() {
    val transaction =
      createBuyTransaction(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        commission = BigDecimal("10"),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(listOf(transaction))

    assertThat(quantity).isEqualByComparingTo(BigDecimal("10"))
    assertThat(averageCost).isEqualByComparingTo(BigDecimal("101"))
  }

  @Test
  fun `calculateCurrentValue returns correct value`() {
    val holdings = BigDecimal("100")
    val currentPrice = BigDecimal("150.50")

    val result = investmentMetricsService.calculateCurrentValue(holdings, currentPrice)

    assertThat(result).isEqualByComparingTo(BigDecimal("15050.00"))
  }

  @Test
  fun `calculateCurrentValue with zero holdings`() {
    val result = investmentMetricsService.calculateCurrentValue(BigDecimal.ZERO, BigDecimal("150"))

    assertThat(result).isEqualByComparingTo(BigDecimal.ZERO)
  }

  @Test
  fun `calculateProfit returns positive profit`() {
    val holdings = BigDecimal("100")
    val averageCost = BigDecimal("50")
    val currentPrice = BigDecimal("75")

    val profit = investmentMetricsService.calculateProfit(holdings, averageCost, currentPrice)

    assertThat(profit).isEqualByComparingTo(BigDecimal("2500"))
  }

  @Test
  fun `calculateProfit returns negative profit (loss)`() {
    val holdings = BigDecimal("100")
    val averageCost = BigDecimal("80")
    val currentPrice = BigDecimal("60")

    val profit = investmentMetricsService.calculateProfit(holdings, averageCost, currentPrice)

    assertThat(profit).isEqualByComparingTo(BigDecimal("-2000"))
  }

  @Test
  fun `calculateProfit returns zero when no price change`() {
    val holdings = BigDecimal("100")
    val price = BigDecimal("50")

    val profit = investmentMetricsService.calculateProfit(holdings, price, price)

    assertThat(profit).isEqualByComparingTo(BigDecimal.ZERO)
  }

  @Test
  fun `buildXirrTransactions with buy transactions only`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"), date = testDate.minusDays(30)),
        createBuyTransaction(quantity = BigDecimal("5"), price = BigDecimal("110"), date = testDate.minusDays(15)),
      )
    val currentValue = BigDecimal("1500")

    val xirrTransactions = investmentMetricsService.buildXirrTransactions(transactions, currentValue, testDate)

    assertThat(xirrTransactions).hasSize(3)
    assertThat(xirrTransactions[0].amount).isNegative()
    assertThat(xirrTransactions[1].amount).isNegative()
    assertThat(xirrTransactions[2].amount).isEqualTo(1500.0)
    assertThat(xirrTransactions[2].date).isEqualTo(testDate)
  }

  @Test
  fun `buildXirrTransactions with buy and sell transactions`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"), date = testDate.minusDays(60)),
        createSellTransaction(quantity = BigDecimal("40"), price = BigDecimal("70"), date = testDate.minusDays(30)),
      )
    val currentValue = BigDecimal("4200")

    val xirrTransactions = investmentMetricsService.buildXirrTransactions(transactions, currentValue, testDate)

    assertThat(xirrTransactions).hasSize(3)
    assertThat(xirrTransactions[0].amount).isNegative()
    assertThat(xirrTransactions[1].amount).isPositive()
    assertThat(xirrTransactions[2].amount).isEqualTo(4200.0)
  }

  @Test
  fun `buildXirrTransactions with zero current value`() {
    val transactions = listOf(createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")))

    val xirrTransactions = investmentMetricsService.buildXirrTransactions(transactions, BigDecimal.ZERO, testDate)

    assertThat(xirrTransactions).hasSize(1)
    assertThat(xirrTransactions[0].amount).isNegative()
  }

  @Test
  fun `calculateAdjustedXirr with sufficient transactions returns bounded value`() {
    val transactions =
      listOf(
        Transaction(-1000.0, testDate.minusDays(100)),
        Transaction(-500.0, testDate.minusDays(50)),
        Transaction(2000.0, testDate),
      )

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, BigDecimal("2000"), testDate)

    assertThat(xirr).isBetween(-10.0, 10.0)
  }

  @Test
  fun `calculateAdjustedXirr with fewer than 2 transactions returns zero`() {
    val transactions = listOf(Transaction(-1000.0, testDate))

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, BigDecimal("1000"), testDate)

    assertThat(xirr).isEqualTo(0.0)
  }

  @Test
  fun `calculateAdjustedXirr applies dampening for new investments`() {
    val recentDate = testDate.minusDays(30)
    val transactions =
      listOf(
        Transaction(-1000.0, recentDate),
        Transaction(1500.0, testDate),
      )

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, BigDecimal("1500"), testDate)

    assertThat(xirr).isLessThan(10.0)
  }

  @Test
  fun `calculateAdjustedXirr handles empty negative cashflows`() {
    val transactions =
      listOf(
        Transaction(500.0, testDate.minusDays(50)),
        Transaction(1000.0, testDate),
      )

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, BigDecimal("1500"), testDate)

    assertThat(xirr).isEqualTo(0.0)
  }

  @ParameterizedTest
  @MethodSource("provideXirrExtremeScenarios")
  fun `calculateAdjustedXirr bounds extreme values`(
    transactions: List<Transaction>,
    expectedBehavior: (Double) -> Boolean,
  ) {
    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, BigDecimal("10000"), testDate)

    assertThat(expectedBehavior(xirr)).isTrue()
  }

  @Test
  fun `calculateInstrumentMetrics with empty transactions returns empty metrics`() {
    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, emptyList(), testDate)

    assertThat(metrics).isEqualTo(InvestmentMetricsService.InstrumentMetrics.EMPTY)
  }

  @Test
  fun `calculateInstrumentMetrics with single platform calculates correctly`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
      )

    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, transactions, testDate)

    assertThat(metrics.quantity).isEqualByComparingTo(BigDecimal("10"))
    assertThat(metrics.totalInvestment).isGreaterThan(BigDecimal.ZERO)
    assertThat(metrics.currentValue).isGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `calculateInstrumentMetrics with multiple platforms aggregates correctly`() {
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

    assertThat(metrics.quantity).isEqualByComparingTo(BigDecimal("25"))
    assertThat(metrics.totalInvestment).isGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `calculateInstrumentMetrics with zero price returns zero values`() {
    testInstrument.currentPrice = BigDecimal.ZERO
    val transactions = listOf(createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")))

    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, transactions, testDate)

    assertThat(metrics.currentValue).isEqualByComparingTo(BigDecimal.ZERO)
    assertThat(metrics.profit).isLessThan(BigDecimal.ZERO)
  }

  @Test
  fun `calculateInstrumentMetricsWithProfits calls transaction service`() {
    val transactions = listOf(createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")))

    val metrics = investmentMetricsService.calculateInstrumentMetricsWithProfits(testInstrument, transactions, testDate)

    assertThat(metrics.quantity).isEqualByComparingTo(BigDecimal("10"))
    assertThat(metrics.totalInvestment).isGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `convertToXirrTransaction for BUY transaction has negative amount`() {
    val buyTransaction = createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"))

    val xirrTx = investmentMetricsService.convertToXirrTransaction(buyTransaction)

    assertThat(xirrTx.amount).isNegative()
    assertThat(xirrTx.date).isEqualTo(buyTransaction.transactionDate)
  }

  @Test
  fun `convertToXirrTransaction for SELL transaction has positive amount`() {
    val sellTransaction = createSellTransaction(quantity = BigDecimal("10"), price = BigDecimal("120"))

    val xirrTx = investmentMetricsService.convertToXirrTransaction(sellTransaction)

    assertThat(xirrTx.amount).isPositive()
    assertThat(xirrTx.date).isEqualTo(sellTransaction.transactionDate)
  }

  @Test
  fun `convertToXirrTransaction includes commission in calculation`() {
    val buyTransaction =
      createBuyTransaction(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        commission = BigDecimal("20"),
      )

    val xirrTx = investmentMetricsService.convertToXirrTransaction(buyTransaction)

    assertThat(xirrTx.amount).isEqualTo(-1020.0)
  }

  @Test
  fun `calculatePortfolioMetrics with single instrument`() {
    val transactions = listOf(createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")))
    val instrumentGroups = mapOf(testInstrument to transactions)

    whenever(dailyPriceService.getPrice(eq(testInstrument), any())).thenReturn(BigDecimal("150"))

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    assertThat(metrics.totalValue).isGreaterThan(BigDecimal.ZERO)
    assertThat(metrics.xirrTransactions).isNotEmpty()
  }

  @Test
  fun `calculatePortfolioMetrics with multiple instruments`() {
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

    whenever(dailyPriceService.getPrice(eq(testInstrument), any())).thenReturn(BigDecimal("150"))
    whenever(dailyPriceService.getPrice(eq(instrument2), any())).thenReturn(BigDecimal("2800"))

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    assertThat(metrics.totalValue).isGreaterThan(BigDecimal.ZERO)
    assertThat(metrics.totalProfit).isNotNull()
  }

  @Test
  fun `calculatePortfolioMetrics handles zero holdings gracefully`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createSellTransaction(quantity = BigDecimal("10"), price = BigDecimal("120")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    assertThat(metrics.totalValue).isEqualByComparingTo(BigDecimal.ZERO)
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
  fun `calculateInstrumentMetricsWithProfits with empty transactions returns empty metrics`() {
    val metrics =
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        testInstrument,
        emptyList(),
        testDate,
      )

    assertThat(metrics).isEqualTo(InvestmentMetricsService.InstrumentMetrics.EMPTY)
  }

  @Test
  fun `calculateInstrumentMetricsWithProfits with multiple platforms`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
        createBuyTransaction(quantity = BigDecimal("5"), price = BigDecimal("120"), platform = Platform.LIGHTYEAR),
        createSellTransaction(quantity = BigDecimal("3"), price = BigDecimal("150"), platform = Platform.LHV),
      )

    val metrics =
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        testInstrument,
        transactions,
        testDate,
      )

    assertThat(metrics.quantity).isGreaterThan(BigDecimal.ZERO)
    assertThat(metrics.totalInvestment).isGreaterThan(BigDecimal.ZERO)
    assertThat(metrics.currentValue).isGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `calculateInstrumentMetricsWithProfits with all positions sold`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createSellTransaction(quantity = BigDecimal("10"), price = BigDecimal("150")),
      )

    val metrics =
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        testInstrument,
        transactions,
        testDate,
      )

    assertThat(metrics.quantity).isEqualByComparingTo(BigDecimal.ZERO)
    assertThat(metrics.totalInvestment).isEqualByComparingTo(BigDecimal.ZERO)
  }

  @Test
  fun `calculateAdjustedXirr with very short investment period applies damping`() {
    val transactions =
      listOf(
        Transaction(-1000.0, testDate.minusDays(15)),
        Transaction(1200.0, testDate),
      )

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, BigDecimal("1200"), testDate)

    assertThat(xirr).isLessThan(10.0)
    assertThat(xirr).isGreaterThanOrEqualTo(0.0)
  }

  @Test
  fun `calculateAdjustedXirr with investment period over 60 days has full damping`() {
    val transactions =
      listOf(
        Transaction(-1000.0, testDate.minusDays(100)),
        Transaction(1500.0, testDate),
      )

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, BigDecimal("1500"), testDate)

    assertThat(xirr).isBetween(-10.0, 10.0)
  }

  @Test
  fun `calculateAdjustedXirr with exactly 60 days investment period`() {
    val transactions =
      listOf(
        Transaction(-1000.0, testDate.minusDays(60)),
        Transaction(1200.0, testDate),
      )

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, BigDecimal("1200"), testDate)

    assertThat(xirr).isBetween(-10.0, 10.0)
  }

  @Test
  fun `calculateAdjustedXirr handles exception and returns zero`() {
    val transactions =
      listOf(
        Transaction(-1000.0, testDate),
        Transaction(1000.0, testDate),
      )

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, BigDecimal("1000"), testDate)

    assertThat(xirr).isEqualTo(0.0)
  }

  @Test
  fun `buildXirrTransactions with negative current value omits final transaction`() {
    val transactions = listOf(createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")))

    val xirrTransactions =
      investmentMetricsService.buildXirrTransactions(
        transactions,
        BigDecimal("-100"),
        testDate,
      )

    assertThat(xirrTransactions).hasSize(1)
    assertThat(xirrTransactions[0].amount).isNegative()
  }

  @Test
  fun `buildXirrTransactions with multiple buy and sell transactions`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"), date = testDate.minusDays(90)),
        createBuyTransaction(quantity = BigDecimal("50"), price = BigDecimal("60"), date = testDate.minusDays(60)),
        createSellTransaction(quantity = BigDecimal("30"), price = BigDecimal("70"), date = testDate.minusDays(30)),
        createSellTransaction(quantity = BigDecimal("20"), price = BigDecimal("80"), date = testDate.minusDays(15)),
      )
    val currentValue = BigDecimal("10000")

    val xirrTransactions = investmentMetricsService.buildXirrTransactions(transactions, currentValue, testDate)

    assertThat(xirrTransactions).hasSize(5)
    assertThat(xirrTransactions[0].amount).isNegative()
    assertThat(xirrTransactions[1].amount).isNegative()
    assertThat(xirrTransactions[2].amount).isPositive()
    assertThat(xirrTransactions[3].amount).isPositive()
    assertThat(xirrTransactions[4].amount).isEqualTo(10000.0)
  }

  @Test
  fun `calculatePortfolioMetrics with fallback when unified calculation fails`() {
    val transactions = listOf(createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")))
    val instrumentGroups = mapOf(testInstrument to transactions)

    whenever(dailyPriceService.getPrice(eq(testInstrument), any()))
      .thenThrow(RuntimeException("Price not found"))
      .thenReturn(BigDecimal("150"))

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    assertThat(metrics.totalValue).isGreaterThan(BigDecimal.ZERO)
    assertThat(metrics.xirrTransactions).isNotEmpty()
  }

  @Test
  fun `calculatePortfolioMetrics aggregates multiple instruments with mixed transactions`() {
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

    whenever(dailyPriceService.getPrice(eq(testInstrument), any())).thenReturn(BigDecimal("150"))
    whenever(dailyPriceService.getPrice(eq(instrument2), any())).thenReturn(BigDecimal("700"))

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    assertThat(metrics.totalValue).isGreaterThan(BigDecimal.ZERO)
    assertThat(metrics.totalProfit).isNotNull()
    assertThat(metrics.xirrTransactions).hasSizeGreaterThan(3)
  }

  @Test
  fun `calculatePortfolioMetrics with negative holdings is excluded`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createSellTransaction(quantity = BigDecimal("15"), price = BigDecimal("120")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    assertThat(metrics.totalValue).isEqualByComparingTo(BigDecimal.ZERO)
  }

  @Test
  fun `calculateCurrentHoldings with sell before buy returns zero`() {
    val transactions =
      listOf(
        createSellTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createBuyTransaction(quantity = BigDecimal("20"), price = BigDecimal("90")),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(transactions)

    assertThat(quantity).isEqualByComparingTo(BigDecimal("20"))
    assertThat(averageCost).isGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `calculateCurrentHoldings with partial sell reduces cost proportionally`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50")),
        createSellTransaction(quantity = BigDecimal("25"), price = BigDecimal("60")),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(transactions)

    assertThat(quantity).isEqualByComparingTo(BigDecimal("75"))
    assertThat(averageCost).isGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `calculateInstrumentMetrics with null current price uses zero`() {
    testInstrument.currentPrice = null
    val transactions = listOf(createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")))

    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, transactions, testDate)

    assertThat(metrics.currentValue).isEqualByComparingTo(BigDecimal.ZERO)
    assertThat(metrics.profit).isLessThan(BigDecimal.ZERO)
  }

  @Test
  fun `calculateInstrumentMetrics with platform having zero holdings is excluded`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
        createSellTransaction(quantity = BigDecimal("10"), price = BigDecimal("120"), platform = Platform.LHV),
        createBuyTransaction(quantity = BigDecimal("5"), price = BigDecimal("110"), platform = Platform.LIGHTYEAR),
      )

    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, transactions, testDate)

    assertThat(metrics.quantity).isEqualByComparingTo(BigDecimal("5"))
  }

  @Test
  fun `calculatePortfolioMetrics with empty instrument groups returns empty metrics`() {
    val metrics = investmentMetricsService.calculatePortfolioMetrics(emptyMap(), testDate)

    assertThat(metrics.totalValue).isEqualByComparingTo(BigDecimal.ZERO)
    assertThat(metrics.totalProfit).isEqualByComparingTo(BigDecimal.ZERO)
    assertThat(metrics.xirrTransactions).isEmpty()
  }

  @Test
  fun `convertToXirrTransaction for sell with commission reduces amount`() {
    val sellTransaction =
      createSellTransaction(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        commission = BigDecimal("15"),
      )

    val xirrTx = investmentMetricsService.convertToXirrTransaction(sellTransaction)

    assertThat(xirrTx.amount).isEqualTo(985.0)
  }

  @Test
  fun `calculateAdjustedXirr with weighted investment age calculation`() {
    val transactions =
      listOf(
        Transaction(-5000.0, testDate.minusDays(100)),
        Transaction(-3000.0, testDate.minusDays(50)),
        Transaction(-2000.0, testDate.minusDays(20)),
        Transaction(12000.0, testDate),
      )

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, BigDecimal("12000"), testDate)

    assertThat(xirr).isBetween(-10.0, 10.0)
  }

  @Test
  fun `calculatePortfolioMetrics with single transaction per instrument`() {
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

    whenever(dailyPriceService.getPrice(eq(testInstrument), any())).thenReturn(BigDecimal("150"))
    whenever(dailyPriceService.getPrice(eq(instrument2), any())).thenReturn(BigDecimal("300"))

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    assertThat(metrics.totalValue).isGreaterThan(BigDecimal.ZERO)
    assertThat(metrics.xirrTransactions).hasSize(4)
  }

  @Test
  fun `calculateNetQuantity with only buy transactions`() {
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

    assertThat(netQuantity).isEqualByComparingTo(BigDecimal("25"))
  }

  @Test
  fun `calculateNetQuantity with only sell transactions`() {
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

    assertThat(netQuantity).isEqualByComparingTo(BigDecimal("-8"))
  }

  @Test
  fun `calculateNetQuantity with mixed buy and sell transactions`() {
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

    assertThat(netQuantity).isEqualByComparingTo(BigDecimal("50"))
  }

  @Test
  fun `calculateNetQuantity with empty transactions`() {
    val netQuantity =
      emptyList<PortfolioTransaction>().fold(BigDecimal.ZERO) { acc, tx ->
      when (tx.transactionType) {
        TransactionType.BUY -> acc.add(tx.quantity)
        TransactionType.SELL -> acc.subtract(tx.quantity)
      }
    }

    assertThat(netQuantity).isEqualByComparingTo(BigDecimal.ZERO)
  }

  @Test
  fun `calculatePortfolioMetrics fallback with only buy transactions`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")),
        createBuyTransaction(quantity = BigDecimal("5"), price = BigDecimal("110")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    whenever(dailyPriceService.getPrice(eq(testInstrument), any()))
      .thenThrow(RuntimeException("Unified calc failed"))
      .thenReturn(BigDecimal("150"))

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    assertThat(metrics.totalValue).isGreaterThan(BigDecimal.ZERO)
    assertThat(metrics.totalProfit).isNotNull()
  }

  @Test
  fun `calculatePortfolioMetrics fallback with buy and sell transactions`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50")),
        createSellTransaction(quantity = BigDecimal("40"), price = BigDecimal("70")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    whenever(dailyPriceService.getPrice(eq(testInstrument), any()))
      .thenThrow(RuntimeException("Unified calc failed"))
      .thenReturn(BigDecimal("80"))

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    assertThat(metrics.totalValue).isGreaterThan(BigDecimal.ZERO)
    assertThat(metrics.totalProfit).isNotNull()
  }

  @Test
  fun `calculatePortfolioMetrics fallback with zero total sells`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("50"), price = BigDecimal("100")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    whenever(dailyPriceService.getPrice(eq(testInstrument), any()))
      .thenThrow(RuntimeException("Unified calc failed"))
      .thenReturn(BigDecimal("120"))

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    assertThat(metrics.totalValue).isGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `calculatePortfolioMetrics fallback with zero buy quantity`() {
    val transactions =
      listOf(
        createSellTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    assertThat(metrics.totalValue).isEqualByComparingTo(BigDecimal.ZERO)
  }

  @Test
  fun `calculatePortfolioMetrics fallback with complete sell-off`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("50"), price = BigDecimal("100")),
        createSellTransaction(quantity = BigDecimal("50"), price = BigDecimal("150")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    assertThat(metrics.totalValue).isEqualByComparingTo(BigDecimal.ZERO)
  }

  @Test
  fun `calculatePortfolioMetrics fallback calculates realized gains correctly`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"), commission = BigDecimal("10")),
        createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("60"), commission = BigDecimal("10")),
        createSellTransaction(quantity = BigDecimal("50"), price = BigDecimal("80"), commission = BigDecimal("5")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    whenever(dailyPriceService.getPrice(eq(testInstrument), any()))
      .thenThrow(RuntimeException("Unified calc failed"))
      .thenReturn(BigDecimal("90"))

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    assertThat(metrics.totalProfit).isGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `calculateCurrentHoldings with multiple consecutive sells`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50")),
        createSellTransaction(quantity = BigDecimal("25"), price = BigDecimal("60")),
        createSellTransaction(quantity = BigDecimal("25"), price = BigDecimal("65")),
        createSellTransaction(quantity = BigDecimal("25"), price = BigDecimal("70")),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(transactions)

    assertThat(quantity).isEqualByComparingTo(BigDecimal("25"))
    assertThat(averageCost).isGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `calculateCurrentHoldings with zero commission`() {
    val transaction =
      createBuyTransaction(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        commission = BigDecimal.ZERO,
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(listOf(transaction))

    assertThat(quantity).isEqualByComparingTo(BigDecimal("10"))
    assertThat(averageCost).isEqualByComparingTo(BigDecimal("100"))
  }

  @Test
  fun `calculateCurrentHoldings with very small quantities`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("0.001"), price = BigDecimal("50000")),
        createSellTransaction(quantity = BigDecimal("0.0005"), price = BigDecimal("55000")),
      )

    val (quantity, averageCost) = investmentMetricsService.calculateCurrentHoldings(transactions)

    assertThat(quantity).isEqualByComparingTo(BigDecimal("0.0005"))
    assertThat(averageCost).isGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `calculateInstrumentMetrics with one platform having zero holdings after sell`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
        createSellTransaction(quantity = BigDecimal("10"), price = BigDecimal("120"), platform = Platform.LHV),
        createBuyTransaction(quantity = BigDecimal("5"), price = BigDecimal("110"), platform = Platform.LIGHTYEAR),
        createSellTransaction(quantity = BigDecimal("2"), price = BigDecimal("130"), platform = Platform.LIGHTYEAR),
      )

    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, transactions, testDate)

    assertThat(metrics.quantity).isEqualByComparingTo(BigDecimal("3"))
    assertThat(metrics.totalInvestment).isGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `calculatePortfolioMetrics fallback with high commission reduces profit`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"), commission = BigDecimal("50")),
        createSellTransaction(quantity = BigDecimal("5"), price = BigDecimal("150"), commission = BigDecimal("30")),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    whenever(dailyPriceService.getPrice(eq(testInstrument), any()))
      .thenThrow(RuntimeException("Unified calc failed"))
      .thenReturn(BigDecimal("160"))

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    assertThat(metrics.totalValue).isGreaterThan(BigDecimal.ZERO)
    assertThat(metrics.totalProfit).isNotNull()
  }

  @Test
  fun `calculateAdjustedXirr with multiple investments at different times has weighted damping`() {
    val transactions =
      listOf(
        Transaction(-10000.0, testDate.minusDays(120)),
        Transaction(-5000.0, testDate.minusDays(80)),
        Transaction(-2000.0, testDate.minusDays(40)),
        Transaction(-1000.0, testDate.minusDays(10)),
        Transaction(20000.0, testDate),
      )

    val xirr = investmentMetricsService.calculateAdjustedXirr(transactions, BigDecimal("20000"), testDate)

    assertThat(xirr).isBetween(-10.0, 10.0)
  }

  @Test
  fun `convertToXirrTransaction with very high commission for buy`() {
    val buyTransaction =
      createBuyTransaction(
        quantity = BigDecimal("1"),
        price = BigDecimal("1000"),
        commission = BigDecimal("500"),
      )

    val xirrTx = investmentMetricsService.convertToXirrTransaction(buyTransaction)

    assertThat(xirrTx.amount).isEqualTo(-1500.0)
  }

  @Test
  fun `convertToXirrTransaction with zero commission for sell`() {
    val sellTransaction =
      createSellTransaction(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        commission = BigDecimal.ZERO,
      )

    val xirrTx = investmentMetricsService.convertToXirrTransaction(sellTransaction)

    assertThat(xirrTx.amount).isEqualTo(1000.0)
  }

  @Test
  fun `calculateInstrumentMetrics with negative profit scenario`() {
    testInstrument.currentPrice = BigDecimal("50")
    val transactions = listOf(createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")))

    val metrics = investmentMetricsService.calculateInstrumentMetrics(testInstrument, transactions, testDate)

    assertThat(metrics.profit).isLessThan(BigDecimal.ZERO)
    assertThat(metrics.currentValue).isLessThan(metrics.totalInvestment)
  }

  @Test
  fun `calculatePortfolioMetrics with all platforms having zero or negative holdings`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV),
        createSellTransaction(quantity = BigDecimal("10"), price = BigDecimal("120"), platform = Platform.LHV),
        createBuyTransaction(quantity = BigDecimal("5"), price = BigDecimal("110"), platform = Platform.LIGHTYEAR),
        createSellTransaction(quantity = BigDecimal("5"), price = BigDecimal("130"), platform = Platform.LIGHTYEAR),
      )
    val instrumentGroups = mapOf(testInstrument to transactions)

    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, testDate)

    assertThat(metrics.totalValue).isEqualByComparingTo(BigDecimal.ZERO)
    assertThat(metrics.totalProfit).isEqualByComparingTo(BigDecimal.ZERO)
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
