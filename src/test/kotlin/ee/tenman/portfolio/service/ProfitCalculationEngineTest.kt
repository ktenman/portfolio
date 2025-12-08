package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class ProfitCalculationEngineTest {
  private lateinit var engine: ProfitCalculationEngine
  private lateinit var testInstrument: Instrument
  private val testDate = LocalDate.of(2024, 1, 15)

  @BeforeEach
  fun setUp() {
    engine = ProfitCalculationEngine()
    testInstrument =
      Instrument(
        symbol = "AAPL",
        name = "Apple Inc.",
        category = "Stock",
        baseCurrency = "USD",
        currentPrice = BigDecimal("150.00"),
        providerName = ProviderName.FT,
      ).apply { id = 1L }
  }

  @Test
  fun `should calculate zero realized profit for buy transaction`() {
    val buyTx = createBuyTransaction(BigDecimal("100"), BigDecimal("50"))

    engine.calculateProfitsForPlatform(listOf(buyTx))

    expect(buyTx.realizedProfit).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should calculate unrealized profit using buy price`() {
    val buyTx = createBuyTransaction(BigDecimal("100"), BigDecimal("50"))
    testInstrument.currentPrice = BigDecimal("60")

    engine.calculateProfitsForPlatform(listOf(buyTx))

    expect(buyTx.unrealizedProfit).toEqualNumerically(BigDecimal("1000"))
    expect(buyTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("50"))
  }

  @Test
  fun `should calculate realized profit for sell transaction`() {
    val buyTx = createBuyTransaction(BigDecimal("100"), BigDecimal("50"), testDate.minusDays(10))
    val sellTx = createSellTransaction(BigDecimal("40"), BigDecimal("70"))
    testInstrument.currentPrice = BigDecimal("65")

    engine.calculateProfitsForPlatform(listOf(buyTx, sellTx))

    expect(sellTx.realizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
    expect(sellTx.averageCost).notToEqualNull()
    expect(sellTx.remainingQuantity).toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("60"))
  }

  @Test
  fun `should handle complete selloff`() {
    val buyTx = createBuyTransaction(BigDecimal("50"), BigDecimal("100"), testDate.minusDays(10))
    val sellTx = createSellTransaction(BigDecimal("50"), BigDecimal("120"))

    engine.calculateProfitsForPlatform(listOf(buyTx, sellTx))

    expect(sellTx.realizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should handle sell only scenario`() {
    val sellTx = createSellTransaction(BigDecimal("50"), BigDecimal("100"))

    engine.calculateProfitsForPlatform(listOf(sellTx))

    expect(sellTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
    val expectedProfit = BigDecimal("50").multiply(BigDecimal("100")).subtract(BigDecimal("5"))
    expect(sellTx.realizedProfit).notToEqualNull().toEqualNumerically(expectedProfit)
  }

  @Test
  fun `should distribute unrealized profit proportionally to multiple buys`() {
    val buy1 = createBuyTransaction(BigDecimal("60"), BigDecimal("50"), testDate.minusDays(20))
    val buy2 = createBuyTransaction(BigDecimal("40"), BigDecimal("50"), testDate.minusDays(10))
    testInstrument.currentPrice = BigDecimal("70")

    engine.calculateProfitsForPlatform(listOf(buy1, buy2))

    val totalUnrealizedProfit = buy1.unrealizedProfit.add(buy2.unrealizedProfit)
    val expectedTotalProfit = BigDecimal("100").multiply(BigDecimal("70").subtract(BigDecimal("50")))
    expect(totalUnrealizedProfit).toEqualNumerically(expectedTotalProfit)
    expect(buy1.unrealizedProfit).toBeGreaterThan(buy2.unrealizedProfit)
  }

  @Test
  fun `should sort transactions by date`() {
    val laterTx = createBuyTransaction(BigDecimal("50"), BigDecimal("60"), testDate)
    val earlierTx = createBuyTransaction(BigDecimal("50"), BigDecimal("40"), testDate.minusDays(10))
    testInstrument.currentPrice = BigDecimal("55")

    engine.calculateProfitsForPlatform(listOf(laterTx, earlierTx))

    expect(earlierTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("40"))
    expect(laterTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("60"))
  }

  @Test
  fun `should use passed current price when provided`() {
    val buyTx = createBuyTransaction(BigDecimal("100"), BigDecimal("50"))
    testInstrument.currentPrice = BigDecimal("60")
    val passedPrice = BigDecimal("80")

    engine.calculateProfitsForPlatform(listOf(buyTx), passedPrice)

    expect(buyTx.unrealizedProfit).toEqualNumerically(BigDecimal("3000"))
  }

  @Test
  fun `should set zero unrealized profit when current price is zero`() {
    val buyTx = createBuyTransaction(BigDecimal("100"), BigDecimal("50"))
    testInstrument.currentPrice = BigDecimal.ZERO

    engine.calculateProfitsForPlatform(listOf(buyTx))

    expect(buyTx.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculate average cost correctly`() {
    val result = engine.calculateAverageCost(BigDecimal("1000"), BigDecimal("10"))
    expect(result).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should return zero average cost when quantity is zero`() {
    val result = engine.calculateAverageCost(BigDecimal("1000"), BigDecimal.ZERO)
    expect(result).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculate unrealized profit correctly`() {
    val result = engine.calculateUnrealizedProfit(BigDecimal("10"), BigDecimal("150"), BigDecimal("100"))
    expect(result).toEqualNumerically(BigDecimal("500"))
  }

  private fun createBuyTransaction(
    quantity: BigDecimal,
    price: BigDecimal,
    date: LocalDate = testDate,
    commission: BigDecimal = BigDecimal("5"),
  ): PortfolioTransaction =
    PortfolioTransaction(
      instrument = testInstrument,
      transactionType = TransactionType.BUY,
      quantity = quantity,
      price = price,
      transactionDate = date,
      platform = Platform.LHV,
      commission = commission,
    )

  private fun createSellTransaction(
    quantity: BigDecimal,
    price: BigDecimal,
    date: LocalDate = testDate,
    commission: BigDecimal = BigDecimal("5"),
  ): PortfolioTransaction =
    PortfolioTransaction(
      instrument = testInstrument,
      transactionType = TransactionType.SELL,
      quantity = quantity,
      price = price,
      transactionDate = date,
      platform = Platform.LHV,
      commission = commission,
    )
}
