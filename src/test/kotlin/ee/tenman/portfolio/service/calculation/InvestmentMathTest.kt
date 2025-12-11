package ee.tenman.portfolio.service.calculation

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.testing.fixture.TransactionFixtures
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class InvestmentMathTest {
  private val testInstrument = TransactionFixtures.createInstrument()

  @Test
  fun `should calculateRealizedProfit with sell transactions having realized profits`() {
    val transactions =
      listOf(
        TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("10"), BigDecimal("100")),
        TransactionFixtures.createSellTransaction(testInstrument, BigDecimal("5"), BigDecimal("120")).apply {
          realizedProfit = BigDecimal("100")
        },
        TransactionFixtures.createSellTransaction(testInstrument, BigDecimal("3"), BigDecimal("130")).apply {
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
        TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("10"), BigDecimal("100")),
        TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("5"), BigDecimal("110")),
      )
    val result = InvestmentMath.calculateRealizedProfit(transactions)
    expect(result).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateRealizedProfit handles null realized profit as zero`() {
    val transactions =
      listOf(
        TransactionFixtures.createSellTransaction(testInstrument, BigDecimal("5"), BigDecimal("120")).apply {
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
        TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("10"), BigDecimal("100")),
        TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("5"), BigDecimal("120"), commission = BigDecimal("3")),
      )
    val result = InvestmentMath.calculateTotalBuys(transactions)
    expect(result).toEqualNumerically(BigDecimal("1608"))
  }

  @Test
  fun `should calculateTotalBuys excludes sell transactions`() {
    val transactions =
      listOf(
        TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("10"), BigDecimal("100")),
        TransactionFixtures.createSellTransaction(testInstrument, BigDecimal("5"), BigDecimal("120"), commission = BigDecimal("3")),
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
        TransactionFixtures.createSellTransaction(testInstrument, BigDecimal("10"), BigDecimal("120")),
        TransactionFixtures.createSellTransaction(testInstrument, BigDecimal("5"), BigDecimal("130"), commission = BigDecimal("3")),
      )
    val result = InvestmentMath.calculateTotalSells(transactions)
    expect(result).toEqualNumerically(BigDecimal("1842"))
  }

  @Test
  fun `should calculateTotalSells excludes buy transactions`() {
    val transactions =
      listOf(
        TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("10"), BigDecimal("100")),
        TransactionFixtures.createSellTransaction(testInstrument, BigDecimal("5"), BigDecimal("120"), commission = BigDecimal("3")),
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
        TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("10"), BigDecimal("100")),
        TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("15"), BigDecimal("110")),
      )
    val result = InvestmentMath.calculateBuyQuantity(transactions)
    expect(result).toEqualNumerically(BigDecimal("25"))
  }

  @Test
  fun `should calculateBuyQuantity excludes sell transactions`() {
    val transactions =
      listOf(
        TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("10"), BigDecimal("100")),
        TransactionFixtures.createSellTransaction(testInstrument, BigDecimal("5"), BigDecimal("120")),
      )
    val result = InvestmentMath.calculateBuyQuantity(transactions)
    expect(result).toEqualNumerically(BigDecimal("10"))
  }

  @Test
  fun `should calculateSellQuantity with multiple sell transactions`() {
    val transactions =
      listOf(
        TransactionFixtures.createSellTransaction(testInstrument, BigDecimal("5"), BigDecimal("120")),
        TransactionFixtures.createSellTransaction(testInstrument, BigDecimal("3"), BigDecimal("130")),
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
        TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("100"), BigDecimal("50"), commission = BigDecimal.ZERO),
        TransactionFixtures.createSellTransaction(testInstrument, BigDecimal("40"), BigDecimal("60"), commission = BigDecimal.ZERO),
      )
    val totalBuys = BigDecimal("5000")
    val result = InvestmentMath.calculateSoldCost(transactions, totalBuys)
    expect(result).toEqualNumerically(BigDecimal("2000"))
  }

  @Test
  fun `should calculateSoldCost returns zero when no buy quantity`() {
    val transactions =
      listOf(
        TransactionFixtures.createSellTransaction(testInstrument, BigDecimal("10"), BigDecimal("100")),
      )
    val result = InvestmentMath.calculateSoldCost(transactions, BigDecimal("1000"))
    expect(result).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateFallbackProfits with mixed transactions`() {
    val transactions =
      listOf(
        TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("100"), BigDecimal("50"), commission = BigDecimal("10")),
        TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("50"), BigDecimal("60")),
        TransactionFixtures.createSellTransaction(testInstrument, BigDecimal("30"), BigDecimal("80")),
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
        TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("100"), BigDecimal("50"), commission = BigDecimal("10")),
      )
    val currentValue = BigDecimal("6000")
    val (realizedGains, unrealizedGains) = InvestmentMath.calculateFallbackProfits(transactions, currentValue)
    expect(realizedGains).toEqualNumerically(BigDecimal.ZERO)
    expect(unrealizedGains.compareTo(BigDecimal.ZERO) > 0).toEqual(true)
  }
}
