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
