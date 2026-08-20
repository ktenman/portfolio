package ee.tenman.portfolio.service.transaction

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode

class TransactionServiceProfitInternalsTest : TransactionServiceTestBase() {
  @Test
  fun `should processBuyTransaction accumulates cost correctly with commission`() {
    val buyTx = createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50"), commission = BigDecimal("25"))

    transactionService.calculateTransactionProfits(listOf(buyTx))

    expect(buyTx.realizedProfit).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("100"))
    expect(buyTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("50"))
  }

  @Test
  fun `should processSellTransaction calculates realized profit with average cost`() {
    val buyTx = createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("40"), date = testDate.minusDays(10))
    val sellTx = createSellCashFlow(quantity = BigDecimal("50"), price = BigDecimal("60"), commission = BigDecimal("10"))

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    val avgCost =
      BigDecimal("40")
        .multiply(BigDecimal("100"))
        .add(BigDecimal("5"))
        .divide(BigDecimal("100"), 10, RoundingMode.HALF_UP)
    val grossProfit = BigDecimal("50").multiply(BigDecimal("60").subtract(avgCost))
    val expectedProfit = grossProfit.subtract(BigDecimal("10"))

    expect(sellTx.realizedProfit).notToEqualNull().toEqualNumerically(expectedProfit)
    expect(sellTx.averageCost).notToEqualNull().toEqualNumerically(avgCost)
    expect(sellTx.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
    expect(sellTx.remainingQuantity).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should processSellTransaction reduces total cost proportionally`() {
    val buyTx = createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50"), date = testDate.minusDays(10))
    val sellTx = createSellCashFlow(quantity = BigDecimal("40"), price = BigDecimal("70"))
    testInstrument.currentPrice = BigDecimal("65")

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("60"))
    expect(sellTx.realizedProfit).notToEqualNull().toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateAverageCost returns zero when quantity is zero`() {
    val sellTx = createSellCashFlow(quantity = BigDecimal("50"), price = BigDecimal("100"))

    transactionService.calculateTransactionProfits(listOf(sellTx))

    expect(sellTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateAverageCost divides total cost by quantity correctly`() {
    val buy1 =
      createBuyCashFlow(
        quantity = BigDecimal("30"),
        price = BigDecimal("100"),
        commission = BigDecimal("15"),
        date = testDate.minusDays(20),
      )
    val buy2 =
      createBuyCashFlow(
        quantity = BigDecimal("70"),
        price = BigDecimal("120"),
        commission = BigDecimal("35"),
        date = testDate.minusDays(10),
      )

    transactionService.calculateTransactionProfits(listOf(buy1, buy2))

    expect(buy1.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("100"))
    expect(buy2.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("120"))
  }

  @Test
  fun `should distributeUnrealizedProfits sets zero metrics when current quantity is zero`() {
    val buyTx = createBuyCashFlow(quantity = BigDecimal("50"), price = BigDecimal("100"), date = testDate.minusDays(10))
    val sellTx = createSellCashFlow(quantity = BigDecimal("50"), price = BigDecimal("110"))

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.averageCost).notToEqualNull().toEqualNumerically(buyTx.price)
  }

  @Test
  fun `should distributeUnrealizedProfits calculates proportional quantities for multiple buys`() {
    val buy1 = createBuyCashFlow(quantity = BigDecimal("30"), price = BigDecimal("50"), date = testDate.minusDays(20))
    val buy2 = createBuyCashFlow(quantity = BigDecimal("70"), price = BigDecimal("50"), date = testDate.minusDays(10))
    testInstrument.currentPrice = BigDecimal("60")

    transactionService.calculateTransactionProfits(listOf(buy1, buy2))

    val totalRemaining = buy1.remainingQuantity.add(buy2.remainingQuantity)
    expect(totalRemaining).toEqualNumerically(BigDecimal("100"))

    val ratio1 = buy1.remainingQuantity.divide(totalRemaining, 10, RoundingMode.HALF_UP)
    val ratio2 = buy2.remainingQuantity.divide(totalRemaining, 10, RoundingMode.HALF_UP)

    expect(ratio1).toEqualNumerically(BigDecimal("0.3"))
    expect(ratio2).toEqualNumerically(BigDecimal("0.7"))
  }

  @Test
  fun `should distributeUnrealizedProfits distributes profit proportionally to remaining quantity`() {
    val buy1 = createBuyCashFlow(quantity = BigDecimal("40"), price = BigDecimal("50"), date = testDate.minusDays(20))
    val buy2 = createBuyCashFlow(quantity = BigDecimal("60"), price = BigDecimal("50"), date = testDate.minusDays(10))
    testInstrument.currentPrice = BigDecimal("70")

    transactionService.calculateTransactionProfits(listOf(buy1, buy2))

    val totalUnrealizedProfit = buy1.unrealizedProfit.add(buy2.unrealizedProfit)
    val expectedTotalProfit = BigDecimal("100").multiply(BigDecimal("70").subtract(BigDecimal("50")))

    expect(totalUnrealizedProfit).toEqualNumerically(expectedTotalProfit)

    val profitRatio1 = buy1.unrealizedProfit.divide(totalUnrealizedProfit, 10, RoundingMode.HALF_UP)
    val profitRatio2 = buy2.unrealizedProfit.divide(totalUnrealizedProfit, 10, RoundingMode.HALF_UP)

    expect(profitRatio1).toEqualNumerically(BigDecimal("0.4"))
    expect(profitRatio2).toEqualNumerically(BigDecimal("0.6"))
  }
}
