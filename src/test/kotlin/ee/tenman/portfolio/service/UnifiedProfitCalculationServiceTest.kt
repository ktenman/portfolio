package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.service.xirr.Transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.stream.Stream

class UnifiedProfitCalculationServiceTest {
  private lateinit var service: UnifiedProfitCalculationService
  private val today = LocalDate.now()
  private val instrument = mock(ee.tenman.portfolio.domain.Instrument::class.java)

  @BeforeEach
  fun setUp() {
    service = UnifiedProfitCalculationService()
    whenever(instrument.id).thenReturn(1L)
  }

  @Test
  fun `calculateProfit should return correct profit value for given inputs`() {
    val holdings = BigDecimal("10")
    val averageCost = BigDecimal("20")
    val currentPrice = BigDecimal("25")

    val result = service.calculateProfit(holdings, averageCost, currentPrice)

    assertThat(result).isEqualByComparingTo(BigDecimal("50"))
  }

  @Test
  fun `calculateProfit should return negative value when current price is lower than average cost`() {
    val holdings = BigDecimal("10")
    val averageCost = BigDecimal("25")
    val currentPrice = BigDecimal("20")

    val result = service.calculateProfit(holdings, averageCost, currentPrice)

    assertThat(result).isEqualByComparingTo(BigDecimal("-50"))
  }

  @Test
  fun `calculateCurrentValue should return correct value for given holdings and price`() {
    val holdings = BigDecimal("15")
    val currentPrice = BigDecimal("30")

    val result = service.calculateCurrentValue(holdings, currentPrice)

    assertThat(result).isEqualByComparingTo(BigDecimal("450"))
  }

  @Test
  fun `calculateAdjustedXirr should return zero when fewer than two transactions are provided`() {
    val transactions = listOf(Transaction(-1000.0, today.minusDays(30)))

    val result = service.calculateAdjustedXirr(transactions, BigDecimal("1000"), today)

    assertThat(result).isZero()
  }

  @Test
  fun `calculateAdjustedXirr should return zero when no cash outflows are present`() {
    val transactions =
      listOf(
        Transaction(500.0, today.minusDays(30)),
        Transaction(500.0, today),
      )

    val result = service.calculateAdjustedXirr(transactions, BigDecimal("1000"), today)

    assertThat(result).isZero()
  }

  @Test
  fun `calculateAdjustedXirr should apply dampening factor for recent investments`() {
    val recentTransactions =
      listOf(
        Transaction(-1000.0, today.minusDays(1)),
        Transaction(1010.0, today),
      )

    val result = service.calculateAdjustedXirr(recentTransactions, BigDecimal("1010"), today)

    assertThat(result).isEqualTo(0.16666666666666666)

    val olderTransactions =
      listOf(
        Transaction(-1000.0, today.minusDays(180)),
        Transaction(1100.0, today),
      )

    val olderResult = service.calculateAdjustedXirr(olderTransactions, BigDecimal("1100"), today)

    assertThat(olderResult).isGreaterThan(0.15)
  }

  @Test
  fun `calculateAdjustedXirr should bound extreme values within reasonable limits`() {
    val highReturnTransactions =
      listOf(
        Transaction(-1000.0, today.minusYears(1)),
        Transaction(100000.0, today),
      )

    val result = service.calculateAdjustedXirr(highReturnTransactions, BigDecimal("100000"), today)

    assertThat(result).isLessThanOrEqualTo(10.0)

    val lowReturnTransactions =
      listOf(
        Transaction(-10000.0, today.minusYears(1)),
        Transaction(10.0, today),
      )

    val lowResult = service.calculateAdjustedXirr(lowReturnTransactions, BigDecimal("10"), today)

    assertThat(lowResult).isGreaterThanOrEqualTo(-10.0)
  }

  @Test
  fun `calculateAdjustedXirr should handle multiple cash flows correctly`() {
    val transactions =
      listOf(
        Transaction(-1000.0, today.minusYears(2)),
        Transaction(-500.0, today.minusYears(1)),
        Transaction(-200.0, today.minusMonths(6)),
        Transaction(2000.0, today),
      )

    val result = service.calculateAdjustedXirr(transactions, BigDecimal("2000"), today)

    assertThat(result).isBetween(0.0, 0.3)
  }

  @ParameterizedTest(name = "Market condition: {0}")
  @MethodSource("marketConditions")
  fun `calculateAdjustedXirr should reflect different market conditions correctly`(
    marketCondition: String,
    transactions: List<Transaction>,
    expectedCondition: (Double) -> Boolean,
  ) {
    val result =
      service.calculateAdjustedXirr(
        transactions,
        BigDecimal(transactions.last().amount.toString()),
        today,
      )

    assertThat(expectedCondition(result)).isTrue()
  }

  @Test
  fun `calculateCurrentHoldings should return correct values with buy transactions only`() {
    val transactions =
      listOf(
        createTransaction(BigDecimal("10"), BigDecimal("100"), today.minusDays(30), TransactionType.BUY),
        createTransaction(BigDecimal("5"), BigDecimal("120"), today.minusDays(15), TransactionType.BUY),
      )

    val (quantity, averageCost) = service.calculateCurrentHoldings(transactions)

    assertThat(quantity).isEqualByComparingTo(BigDecimal("15"))
    assertThat(averageCost).isEqualByComparingTo(BigDecimal("106.6666666667"))
  }

  @Test
  fun `calculateCurrentHoldings should handle mix of buy and sell transactions correctly`() {
    val transactions =
      listOf(
        createTransaction(BigDecimal("10"), BigDecimal("100"), today.minusDays(30), TransactionType.BUY),
        createTransaction(BigDecimal("5"), BigDecimal("120"), today.minusDays(15), TransactionType.BUY),
        createTransaction(BigDecimal("3"), BigDecimal("130"), today.minusDays(5), TransactionType.SELL),
      )

    val (quantity, averageCost) = service.calculateCurrentHoldings(transactions)

    assertThat(quantity).isEqualByComparingTo(BigDecimal("12"))
    assertThat(averageCost).isEqualByComparingTo(BigDecimal("106.6666666667"))
  }

  @Test
  fun `calculateCurrentHoldings should return zeros when no transactions exist`() {
    val (quantity, averageCost) = service.calculateCurrentHoldings(emptyList())

    assertThat(quantity).isEqualByComparingTo(BigDecimal.ZERO)
    assertThat(averageCost).isEqualByComparingTo(BigDecimal.ZERO)
  }

  @Test
  fun `calculateCurrentHoldings should return zeros when all shares have been sold`() {
    val transactions =
      listOf(
        createTransaction(BigDecimal("10"), BigDecimal("100"), today.minusDays(30), TransactionType.BUY),
        createTransaction(BigDecimal("10"), BigDecimal("110"), today.minusDays(15), TransactionType.SELL),
      )

    val (quantity, averageCost) = service.calculateCurrentHoldings(transactions)

    assertThat(quantity).isEqualByComparingTo(BigDecimal.ZERO)
    assertThat(averageCost).isEqualByComparingTo(BigDecimal.ZERO)
  }

  @Test
  fun `buildXirrTransactions should include current value transaction when value is positive`() {
    val portfolioTransactions =
      listOf(
        createTransaction(BigDecimal("10"), BigDecimal("100"), today.minusDays(30), TransactionType.BUY),
        createTransaction(BigDecimal("5"), BigDecimal("110"), today.minusDays(15), TransactionType.SELL),
      )

    val xirrTransactions = service.buildXirrTransactions(portfolioTransactions, BigDecimal("600"))

    assertThat(xirrTransactions).hasSize(3)
    assertThat(xirrTransactions[0].amount).isEqualTo(-1000.0)
    assertThat(xirrTransactions[1].amount).isEqualTo(550.0)
    assertThat(xirrTransactions[2].amount).isEqualTo(600.0)
    assertThat(xirrTransactions[2].date).isEqualTo(LocalDate.now())
  }

  @Test
  fun `buildXirrTransactions should not include current value transaction when value is zero`() {
    val portfolioTransactions =
      listOf(
        createTransaction(BigDecimal("10"), BigDecimal("100"), today.minusDays(30), TransactionType.BUY),
        createTransaction(BigDecimal("5"), BigDecimal("110"), today.minusDays(15), TransactionType.SELL),
      )

    val xirrTransactions = service.buildXirrTransactions(portfolioTransactions, BigDecimal.ZERO)

    assertThat(xirrTransactions).hasSize(2)
    assertThat(xirrTransactions[0].amount).isEqualTo(-1000.0)
    assertThat(xirrTransactions[1].amount).isEqualTo(550.0)
  }

  companion object {
    @JvmStatic
    fun marketConditions(): Stream<Arguments> {
      val today = LocalDate.now()

      return Stream.of(
        Arguments.of(
          "Rising market",
          listOf(
            Transaction(-1000.0, today.minusMonths(6)),
            Transaction(1200.0, today),
          ),
          { xirr: Double -> xirr > 0.0 },
        ),
        Arguments.of(
          "Falling market",
          listOf(
            Transaction(-1000.0, today.minusMonths(6)),
            Transaction(800.0, today),
          ),
          { xirr: Double -> xirr < 0.0 },
        ),
        Arguments.of(
          "Stable market",
          listOf(
            Transaction(-1000.0, today.minusMonths(6)),
            Transaction(1000.0, today),
          ),
          { xirr: Double -> Math.abs(xirr) < 0.01 },
        ),
      )
    }
  }

  private fun createTransaction(
    quantity: BigDecimal,
    price: BigDecimal,
    date: LocalDate,
    type: TransactionType,
  ): PortfolioTransaction =
    PortfolioTransaction(
      instrument = instrument,
      transactionType = type,
      quantity = quantity,
      price = price,
      transactionDate = date,
      platform = Platform.TRADING212,
    )
}
