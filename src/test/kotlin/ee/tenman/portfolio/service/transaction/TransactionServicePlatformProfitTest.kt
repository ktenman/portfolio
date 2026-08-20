package ee.tenman.portfolio.service.transaction

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal

class TransactionServicePlatformProfitTest : TransactionServiceTestBase() {
  @Test
  fun `should calculateProfitsForPlatform handles buy only scenario`() {
    val buy1 = createBuyCashFlow(quantity = BigDecimal("25"), price = BigDecimal("80"), date = testDate.minusDays(15))
    val buy2 = createBuyCashFlow(quantity = BigDecimal("75"), price = BigDecimal("100"), date = testDate.minusDays(5))
    testInstrument.currentPrice = BigDecimal("110")

    transactionService.calculateTransactionProfits(listOf(buy1, buy2))

    expect(buy1.realizedProfit).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
    expect(buy2.realizedProfit).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
    expect(buy1.unrealizedProfit).toBeGreaterThan(BigDecimal.ZERO)
    expect(buy2.unrealizedProfit).toBeGreaterThan(BigDecimal.ZERO)
    expect(buy1.remainingQuantity.add(buy2.remainingQuantity)).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should calculateProfitsForPlatform handles sell only scenario with zero current quantity`() {
    val sellTx = createSellCashFlow(quantity = BigDecimal("50"), price = BigDecimal("100"))

    transactionService.calculateTransactionProfits(listOf(sellTx))

    expect(sellTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
    val expectedProfit = BigDecimal("50").multiply(BigDecimal("100")).subtract(BigDecimal("5"))
    expect(sellTx.realizedProfit).notToEqualNull().toEqualNumerically(expectedProfit)
    expect(sellTx.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
    expect(sellTx.remainingQuantity).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateProfitsForPlatform handles mixed buy sell buy sequence`() {
    val buy1 = createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50"), date = testDate.minusDays(30))
    val sell1 = createSellCashFlow(quantity = BigDecimal("60"), price = BigDecimal("70"), date = testDate.minusDays(15))
    val buy2 = createBuyCashFlow(quantity = BigDecimal("50"), price = BigDecimal("65"), date = testDate.minusDays(5))
    testInstrument.currentPrice = BigDecimal("80")

    transactionService.calculateTransactionProfits(listOf(buy1, sell1, buy2))

    expect(sell1.realizedProfit).notToEqualNull().toBeGreaterThan(BigDecimal.ZERO)
    expect(buy1.remainingQuantity.add(buy2.remainingQuantity)).toEqualNumerically(BigDecimal("90"))
    expect(buy1.unrealizedProfit).toBeGreaterThan(BigDecimal.ZERO)
    expect(buy2.unrealizedProfit).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateProfitsForPlatform handles edge case with single transaction`() {
    val singleBuy = createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("75"))
    testInstrument.currentPrice = BigDecimal("90")

    transactionService.calculateTransactionProfits(listOf(singleBuy))

    expect(singleBuy.realizedProfit).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
    expect(singleBuy.remainingQuantity).toEqualNumerically(BigDecimal("100"))
    expect(singleBuy.unrealizedProfit).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateProfitsForPlatform handles complete selloff then new buy`() {
    val buy1 = createBuyCashFlow(quantity = BigDecimal("50"), price = BigDecimal("100"), date = testDate.minusDays(30))
    val sell1 = createSellCashFlow(quantity = BigDecimal("50"), price = BigDecimal("120"), date = testDate.minusDays(15))
    val buy2 = createBuyCashFlow(quantity = BigDecimal("30"), price = BigDecimal("110"), date = testDate.minusDays(5))
    testInstrument.currentPrice = BigDecimal("125")

    transactionService.calculateTransactionProfits(listOf(buy1, sell1, buy2))

    expect(sell1.realizedProfit).notToEqualNull().toBeGreaterThan(BigDecimal.ZERO)
    expect(buy2.unrealizedProfit).toBeGreaterThan(BigDecimal.ZERO)
    expect(buy1.unrealizedProfit.add(buy2.unrealizedProfit)).toBeGreaterThan(BigDecimal.ZERO)
  }

  @ParameterizedTest
  @CsvSource(value = ["0", "null"], nullValues = ["null"])
  fun `should calculateProfitsForPlatform handles zero or null current price`(currentPrice: BigDecimal?) {
    val buyTx = createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50"))
    testInstrument.currentPrice = currentPrice

    transactionService.calculateTransactionProfits(listOf(buyTx))

    expect(buyTx.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should calculateProfitsForPlatform with oversell scenario`() {
    val buyTx = createBuyCashFlow(quantity = BigDecimal("50"), price = BigDecimal("100"), date = testDate.minusDays(10))
    val sellTx = createSellCashFlow(quantity = BigDecimal("80"), price = BigDecimal("110"))

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    expect(buyTx.remainingQuantity).toBeLessThanOrEqualTo(BigDecimal.ZERO)
    expect(buyTx.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateProfitsForPlatform calculates correct average cost after multiple buys and sells`() {
    val buy1 =
      createBuyCashFlow(
        quantity = BigDecimal("100"),
        price = BigDecimal("40"),
        commission = BigDecimal("10"),
        date = testDate.minusDays(40),
      )
    val buy2 =
      createBuyCashFlow(
        quantity = BigDecimal("100"),
        price = BigDecimal("60"),
        commission = BigDecimal("10"),
        date = testDate.minusDays(30),
      )
    val sell1 =
      createSellCashFlow(
        quantity = BigDecimal("100"),
        price = BigDecimal("70"),
        commission = BigDecimal("15"),
        date = testDate.minusDays(15),
      )
    testInstrument.currentPrice = BigDecimal("80")

    transactionService.calculateTransactionProfits(listOf(buy1, buy2, sell1))

    expect(sell1.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("50.1"))
    expect(buy1.remainingQuantity.add(buy2.remainingQuantity)).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should calculateProfitsForPlatform with high precision decimal values`() {
    val buyTx =
      createBuyCashFlow(quantity = BigDecimal("33.333333"), price = BigDecimal("99.999999"), commission = BigDecimal("3.141592"))
    testInstrument.currentPrice = BigDecimal("123.456789")

    transactionService.calculateTransactionProfits(listOf(buyTx))

    expect(buyTx.realizedProfit).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("33.333333"))
    expect(buyTx.unrealizedProfit).toBeGreaterThan(BigDecimal.ZERO)
  }
}
