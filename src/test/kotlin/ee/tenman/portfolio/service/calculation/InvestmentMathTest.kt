package ee.tenman.portfolio.service.calculation

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class InvestmentMathTest {
  private val testDate = LocalDate.of(2024, 1, 15)
  private val testInstrument =
    Instrument(
    symbol = "AAPL",
    name = "Apple Inc.",
    category = "Stock",
    baseCurrency = "USD",
    currentPrice = BigDecimal("150.00"),
  ).apply { id = 1L }

  @Test
  fun `should calculateRealizedProfit with sell transactions having realized profits`() {
    val transactions =
      listOf(
      createTransaction(TransactionType.BUY, BigDecimal("10"), BigDecimal("100")),
      createTransaction(TransactionType.SELL, BigDecimal("5"), BigDecimal("120")).apply {
        realizedProfit = BigDecimal("100")
      },
      createTransaction(TransactionType.SELL, BigDecimal("3"), BigDecimal("130")).apply {
        realizedProfit = BigDecimal("90")
      },
    )

    val result = InvestmentMath.calculateRealizedProfit(transactions)

    expect(result).toEqualNumerically(BigDecimal("190"))
  }

  @Test
  fun `should calculateRealizedProfit returns zero when no sell transactions`() {
    val transactions =
      listOf(
      createTransaction(TransactionType.BUY, BigDecimal("10"), BigDecimal("100")),
      createTransaction(TransactionType.BUY, BigDecimal("5"), BigDecimal("110")),
    )

    val result = InvestmentMath.calculateRealizedProfit(transactions)

    expect(result).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateRealizedProfit handles null realized profit as zero`() {
    val transactions =
      listOf(
      createTransaction(TransactionType.SELL, BigDecimal("5"), BigDecimal("120")).apply {
        realizedProfit = null
      },
    )

    val result = InvestmentMath.calculateRealizedProfit(transactions)

    expect(result).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateRealizedProfit with empty transactions returns zero`() {
    val result = InvestmentMath.calculateRealizedProfit(emptyList())

    expect(result).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateTotalBuys with multiple buy transactions`() {
    val transactions =
      listOf(
      createTransaction(TransactionType.BUY, BigDecimal("10"), BigDecimal("100"), BigDecimal("5")),
      createTransaction(TransactionType.BUY, BigDecimal("5"), BigDecimal("120"), BigDecimal("3")),
    )

    val result = InvestmentMath.calculateTotalBuys(transactions)

    expect(result).toEqualNumerically(BigDecimal("1608"))
  }

  @Test
  fun `should calculateTotalBuys excludes sell transactions`() {
    val transactions =
      listOf(
      createTransaction(TransactionType.BUY, BigDecimal("10"), BigDecimal("100"), BigDecimal("5")),
      createTransaction(TransactionType.SELL, BigDecimal("5"), BigDecimal("120"), BigDecimal("3")),
    )

    val result = InvestmentMath.calculateTotalBuys(transactions)

    expect(result).toEqualNumerically(BigDecimal("1005"))
  }

  @Test
  fun `should calculateTotalBuys with empty transactions returns zero`() {
    val result = InvestmentMath.calculateTotalBuys(emptyList())

    expect(result).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateTotalSells with multiple sell transactions`() {
    val transactions =
      listOf(
      createTransaction(TransactionType.SELL, BigDecimal("10"), BigDecimal("120"), BigDecimal("5")),
      createTransaction(TransactionType.SELL, BigDecimal("5"), BigDecimal("130"), BigDecimal("3")),
    )

    val result = InvestmentMath.calculateTotalSells(transactions)

    expect(result).toEqualNumerically(BigDecimal("1842"))
  }

  @Test
  fun `should calculateTotalSells excludes buy transactions`() {
    val transactions =
      listOf(
      createTransaction(TransactionType.BUY, BigDecimal("10"), BigDecimal("100"), BigDecimal("5")),
      createTransaction(TransactionType.SELL, BigDecimal("5"), BigDecimal("120"), BigDecimal("3")),
    )

    val result = InvestmentMath.calculateTotalSells(transactions)

    expect(result).toEqualNumerically(BigDecimal("597"))
  }

  @Test
  fun `should calculateTotalSells with empty transactions returns zero`() {
    val result = InvestmentMath.calculateTotalSells(emptyList())

    expect(result).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateBuyQuantity with multiple buy transactions`() {
    val transactions =
      listOf(
      createTransaction(TransactionType.BUY, BigDecimal("10"), BigDecimal("100")),
      createTransaction(TransactionType.BUY, BigDecimal("15"), BigDecimal("110")),
    )

    val result = InvestmentMath.calculateBuyQuantity(transactions)

    expect(result).toEqualNumerically(BigDecimal("25"))
  }

  @Test
  fun `should calculateBuyQuantity excludes sell transactions`() {
    val transactions =
      listOf(
      createTransaction(TransactionType.BUY, BigDecimal("10"), BigDecimal("100")),
      createTransaction(TransactionType.SELL, BigDecimal("5"), BigDecimal("120")),
    )

    val result = InvestmentMath.calculateBuyQuantity(transactions)

    expect(result).toEqualNumerically(BigDecimal("10"))
  }

  @Test
  fun `should calculateSellQuantity with multiple sell transactions`() {
    val transactions =
      listOf(
      createTransaction(TransactionType.SELL, BigDecimal("5"), BigDecimal("120")),
      createTransaction(TransactionType.SELL, BigDecimal("3"), BigDecimal("130")),
    )

    val result = InvestmentMath.calculateSellQuantity(transactions)

    expect(result).toEqualNumerically(BigDecimal("8"))
  }

  @Test
  fun `should calculateRealizedGains with profit scenario`() {
    val sellQuantity = BigDecimal("50")
    val buyQuantity = BigDecimal("100")
    val totalBuys = BigDecimal("5000")
    val totalSells = BigDecimal("3500")

    val result = InvestmentMath.calculateRealizedGains(sellQuantity, buyQuantity, totalBuys, totalSells)

    expect(result).toEqualNumerically(BigDecimal("1000"))
  }

  @Test
  fun `should calculateRealizedGains returns zero when totalSells is zero`() {
    val result =
      InvestmentMath.calculateRealizedGains(
      BigDecimal("50"),
      BigDecimal("100"),
      BigDecimal("5000"),
      BigDecimal.ZERO,
    )

    expect(result).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateRealizedGains returns zero when totalBuys is zero`() {
    val result =
      InvestmentMath.calculateRealizedGains(
      BigDecimal("50"),
      BigDecimal("100"),
      BigDecimal.ZERO,
      BigDecimal("3000"),
    )

    expect(result).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateRealizedGains returns zero when buyQuantity is zero`() {
    val result =
      InvestmentMath.calculateRealizedGains(
      BigDecimal("50"),
      BigDecimal.ZERO,
      BigDecimal("5000"),
      BigDecimal("3000"),
    )

    expect(result).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateUnrealizedGains with profit scenario`() {
    val currentValue = BigDecimal("8000")
    val totalBuys = BigDecimal("5000")
    val soldCost = BigDecimal("2000")

    val result = InvestmentMath.calculateUnrealizedGains(currentValue, totalBuys, soldCost)

    expect(result).toEqualNumerically(BigDecimal("5000"))
  }

  @Test
  fun `should calculateUnrealizedGains with loss scenario`() {
    val currentValue = BigDecimal("2000")
    val totalBuys = BigDecimal("5000")
    val soldCost = BigDecimal("2000")

    val result = InvestmentMath.calculateUnrealizedGains(currentValue, totalBuys, soldCost)

    expect(result).toEqualNumerically(BigDecimal("-1000"))
  }

  @Test
  fun `should calculateSoldCost calculates correctly`() {
    val transactions =
      listOf(
      createTransaction(TransactionType.BUY, BigDecimal("100"), BigDecimal("50"), BigDecimal.ZERO),
      createTransaction(TransactionType.SELL, BigDecimal("40"), BigDecimal("60"), BigDecimal.ZERO),
    )
    val totalBuys = BigDecimal("5000")

    val result = InvestmentMath.calculateSoldCost(transactions, totalBuys)

    expect(result).toEqualNumerically(BigDecimal("2000"))
  }

  @Test
  fun `should calculateSoldCost returns zero when no buy quantity`() {
    val transactions =
      listOf(
      createTransaction(TransactionType.SELL, BigDecimal("10"), BigDecimal("100")),
    )

    val result = InvestmentMath.calculateSoldCost(transactions, BigDecimal("1000"))

    expect(result).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateFallbackProfits with mixed transactions`() {
    val transactions =
      listOf(
      createTransaction(TransactionType.BUY, BigDecimal("100"), BigDecimal("50"), BigDecimal("10")),
      createTransaction(TransactionType.BUY, BigDecimal("50"), BigDecimal("60"), BigDecimal("5")),
      createTransaction(TransactionType.SELL, BigDecimal("30"), BigDecimal("80"), BigDecimal("5")),
    )
    val currentValue = BigDecimal("9600")

    val (realizedGains, unrealizedGains) = InvestmentMath.calculateFallbackProfits(transactions, currentValue)

    expect(realizedGains.compareTo(BigDecimal.ZERO) > 0).toEqual(true)
    expect(unrealizedGains.compareTo(BigDecimal.ZERO) > 0).toEqual(true)
  }

  @Test
  fun `should calculateFallbackProfits with only buy transactions`() {
    val transactions =
      listOf(
      createTransaction(TransactionType.BUY, BigDecimal("100"), BigDecimal("50"), BigDecimal("10")),
    )
    val currentValue = BigDecimal("6000")

    val (realizedGains, unrealizedGains) = InvestmentMath.calculateFallbackProfits(transactions, currentValue)

    expect(realizedGains).toEqualNumerically(BigDecimal.ZERO)
    expect(unrealizedGains.compareTo(BigDecimal.ZERO) > 0).toEqual(true)
  }

  private fun createTransaction(
    type: TransactionType,
    quantity: BigDecimal,
    price: BigDecimal,
    commission: BigDecimal = BigDecimal("5"),
  ): PortfolioTransaction =
    PortfolioTransaction(
    instrument = testInstrument,
    transactionType = type,
    quantity = quantity,
    price = price,
    transactionDate = testDate,
    platform = Platform.LHV,
    commission = commission,
  )
}
