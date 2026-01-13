package ee.tenman.portfolio.service.calculation

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
    val buyTx = createBuyCashFlow(BigDecimal("100"), BigDecimal("50"))

    engine.calculateProfitsForPlatform(listOf(buyTx), BigDecimal.ZERO)

    expect(buyTx.realizedProfit).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should calculate unrealized profit using buy price`() {
    val buyTx = createBuyCashFlow(BigDecimal("100"), BigDecimal("50"))
    testInstrument.currentPrice = BigDecimal("60")

    engine.calculateProfitsForPlatform(listOf(buyTx), BigDecimal.ZERO)

    expect(buyTx.unrealizedProfit).toEqualNumerically(BigDecimal("1000"))
    expect(buyTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("50"))
  }

  @Test
  fun `should calculate realized profit for sell transaction`() {
    val buyTx = createBuyCashFlow(BigDecimal("100"), BigDecimal("50"), testDate.minusDays(10))
    val sellTx = createSellCashFlow(BigDecimal("40"), BigDecimal("70"))
    testInstrument.currentPrice = BigDecimal("65")

    engine.calculateProfitsForPlatform(listOf(buyTx, sellTx), BigDecimal.ZERO)

    expect(sellTx.realizedProfit).notToEqualNull().toBeGreaterThan(BigDecimal.ZERO)
    expect(sellTx.averageCost).notToEqualNull()
    expect(sellTx.remainingQuantity).toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("60"))
  }

  @Test
  fun `should handle complete selloff`() {
    val buyTx = createBuyCashFlow(BigDecimal("50"), BigDecimal("100"), testDate.minusDays(10))
    val sellTx = createSellCashFlow(BigDecimal("50"), BigDecimal("120"))

    engine.calculateProfitsForPlatform(listOf(buyTx, sellTx), BigDecimal.ZERO)

    expect(sellTx.realizedProfit).notToEqualNull().toBeGreaterThan(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should handle sell only scenario`() {
    val sellTx = createSellCashFlow(BigDecimal("50"), BigDecimal("100"))

    engine.calculateProfitsForPlatform(listOf(sellTx), BigDecimal.ZERO)

    expect(sellTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
    val expectedProfit = BigDecimal("50").multiply(BigDecimal("100")).subtract(BigDecimal("5"))
    expect(sellTx.realizedProfit).notToEqualNull().toEqualNumerically(expectedProfit)
  }

  @Test
  fun `should distribute unrealized profit proportionally to multiple buys`() {
    val buy1 = createBuyCashFlow(BigDecimal("60"), BigDecimal("50"), testDate.minusDays(20))
    val buy2 = createBuyCashFlow(BigDecimal("40"), BigDecimal("50"), testDate.minusDays(10))
    testInstrument.currentPrice = BigDecimal("70")

    engine.calculateProfitsForPlatform(listOf(buy1, buy2), BigDecimal.ZERO)

    val totalUnrealizedProfit = buy1.unrealizedProfit.add(buy2.unrealizedProfit)
    val expectedTotalProfit = BigDecimal("100").multiply(BigDecimal("70").subtract(BigDecimal("50")))
    expect(totalUnrealizedProfit).toEqualNumerically(expectedTotalProfit)
    expect(buy1.unrealizedProfit).toBeGreaterThan(buy2.unrealizedProfit)
  }

  @Test
  fun `should sort transactions by date`() {
    val laterTx = createBuyCashFlow(BigDecimal("50"), BigDecimal("60"), testDate)
    val earlierTx = createBuyCashFlow(BigDecimal("50"), BigDecimal("40"), testDate.minusDays(10))
    testInstrument.currentPrice = BigDecimal("55")

    engine.calculateProfitsForPlatform(listOf(laterTx, earlierTx), BigDecimal.ZERO)

    expect(earlierTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("40"))
    expect(laterTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("60"))
  }

  @Test
  fun `should use passed current price when provided`() {
    val buyTx = createBuyCashFlow(BigDecimal("100"), BigDecimal("50"))
    testInstrument.currentPrice = BigDecimal("60")
    val passedPrice = BigDecimal("80")

    engine.calculateProfitsForPlatform(listOf(buyTx), passedPrice)

    expect(buyTx.unrealizedProfit).toEqualNumerically(BigDecimal("3000"))
  }

  @Test
  fun `should set zero unrealized profit when current price is zero`() {
    val buyTx = createBuyCashFlow(BigDecimal("100"), BigDecimal("50"))
    testInstrument.currentPrice = BigDecimal.ZERO

    engine.calculateProfitsForPlatform(listOf(buyTx), BigDecimal.ZERO)

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

  private fun createBuyCashFlow(
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

  private fun createSellCashFlow(
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

  @Test
  fun `should use FIFO for cash instrument when buy then sell then buy again`() {
    val cashInstrument = createCashInstrument()
    val buy1 = createCashTransaction(cashInstrument, TransactionType.BUY, BigDecimal("13719.16"), testDate.minusDays(20))
    val sell1 = createCashTransaction(cashInstrument, TransactionType.SELL, BigDecimal("13719.16"), testDate.minusDays(10))
    val buy2 = createCashTransaction(cashInstrument, TransactionType.BUY, BigDecimal("2804.07"), testDate)

    engine.calculateProfitsForPlatform(listOf(buy1, sell1, buy2), BigDecimal("1"))

    expect(buy1.remainingQuantity).toEqualNumerically(BigDecimal.ZERO)
    expect(buy2.remainingQuantity).toEqualNumerically(BigDecimal("2804.07"))
  }

  @Test
  fun `should use FIFO for cash instrument with partial sell`() {
    val cashInstrument = createCashInstrument()
    val buy1 = createCashTransaction(cashInstrument, TransactionType.BUY, BigDecimal("1000"), testDate.minusDays(20))
    val sell1 = createCashTransaction(cashInstrument, TransactionType.SELL, BigDecimal("600"), testDate.minusDays(10))
    val buy2 = createCashTransaction(cashInstrument, TransactionType.BUY, BigDecimal("500"), testDate)

    engine.calculateProfitsForPlatform(listOf(buy1, sell1, buy2), BigDecimal("1"))

    expect(buy1.remainingQuantity).toEqualNumerically(BigDecimal("400"))
    expect(buy2.remainingQuantity).toEqualNumerically(BigDecimal("500"))
  }

  @Test
  fun `should use FIFO for cash instrument consuming multiple buys`() {
    val cashInstrument = createCashInstrument()
    val buy1 = createCashTransaction(cashInstrument, TransactionType.BUY, BigDecimal("100"), testDate.minusDays(30))
    val buy2 = createCashTransaction(cashInstrument, TransactionType.BUY, BigDecimal("200"), testDate.minusDays(20))
    val sell1 = createCashTransaction(cashInstrument, TransactionType.SELL, BigDecimal("150"), testDate.minusDays(10))

    engine.calculateProfitsForPlatform(listOf(buy1, buy2, sell1), BigDecimal("1"))

    expect(buy1.remainingQuantity).toEqualNumerically(BigDecimal.ZERO)
    expect(buy2.remainingQuantity).toEqualNumerically(BigDecimal("150"))
  }

  @Test
  fun `should use proportional distribution for non-cash instrument`() {
    val buy1 = createBuyCashFlow(BigDecimal("100"), BigDecimal("50"), testDate.minusDays(20))
    val sell1 = createSellCashFlow(BigDecimal("100"), BigDecimal("60"), testDate.minusDays(10))
    val buy2 = createBuyCashFlow(BigDecimal("50"), BigDecimal("55"), testDate)
    testInstrument.currentPrice = BigDecimal("60")

    engine.calculateProfitsForPlatform(listOf(buy1, sell1, buy2), BigDecimal.ZERO)

    expect(buy1.remainingQuantity).toBeGreaterThan(BigDecimal.ZERO)
    expect(buy2.remainingQuantity).toBeGreaterThan(BigDecimal.ZERO)
  }

  private fun createCashInstrument(): Instrument =
    Instrument(
      symbol = "EUR",
      name = "Euro Cash",
      category = "CASH",
      baseCurrency = "EUR",
      currentPrice = BigDecimal("1"),
      providerName = ProviderName.MANUAL,
    ).apply { id = 2L }

  private fun createCashTransaction(
    instrument: Instrument,
    type: TransactionType,
    quantity: BigDecimal,
    date: LocalDate,
  ): PortfolioTransaction =
    PortfolioTransaction(
      instrument = instrument,
      transactionType = type,
      quantity = quantity,
      price = BigDecimal("1"),
      transactionDate = date,
      platform = Platform.SWEDBANK,
      commission = BigDecimal.ZERO,
    )
}
