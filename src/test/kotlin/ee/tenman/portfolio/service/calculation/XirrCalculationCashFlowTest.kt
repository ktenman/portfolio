package ee.tenman.portfolio.service.calculation

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.testing.fixture.CashFlowTestBase
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal

class XirrCalculationCashFlowTest : CashFlowTestBase() {
  private val xirrCalculationService = XirrCalculationService(clock)

  @Test
  fun `should buildCashFlows with buy transactions only`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"), date = testDate.minusDays(30)),
        createBuyCashFlow(quantity = BigDecimal("5"), price = BigDecimal("110"), date = testDate.minusDays(15)),
      )
    val currentValue = BigDecimal("1500")

    val xirrCashFlows = xirrCalculationService.buildCashFlows(transactions, currentValue, testDate)

    expect(xirrCashFlows).toHaveSize(3)
    expect(xirrCashFlows[0].amount).toBeLessThan(0.0)
    expect(xirrCashFlows[1].amount).toBeLessThan(0.0)
    expect(xirrCashFlows[2].amount).toEqual(1500.0)
    expect(xirrCashFlows[2].date).toEqual(testDate)
  }

  @Test
  fun `should buildCashFlows with buy and sell transactions`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50"), date = testDate.minusDays(60)),
        createSellCashFlow(quantity = BigDecimal("40"), price = BigDecimal("70"), date = testDate.minusDays(30)),
      )
    val currentValue = BigDecimal("4200")

    val xirrCashFlows = xirrCalculationService.buildCashFlows(transactions, currentValue, testDate)

    expect(xirrCashFlows).toHaveSize(3)
    expect(xirrCashFlows[0].amount).toBeLessThan(0.0)
    expect(xirrCashFlows[1].amount).toBeGreaterThan(0.0)
    expect(xirrCashFlows[2].amount).toEqual(4200.0)
  }

  @ParameterizedTest
  @CsvSource("0", "-100")
  fun `should buildCashFlows omit the final cash flow when the current value is not positive`(currentValue: BigDecimal) {
    val transactions = listOf(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")))

    val xirrCashFlows = xirrCalculationService.buildCashFlows(transactions, currentValue, testDate)

    expect(xirrCashFlows).toHaveSize(1)
    expect(xirrCashFlows[0].amount).toBeLessThan(0.0)
  }

  @Test
  fun `should convertToCashFlow for BUY transaction has negative amount`() {
    val buyTransaction = createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"))

    val xirrTx = xirrCalculationService.convertToCashFlow(buyTransaction)

    expect(xirrTx.amount).toBeLessThan(0.0)
    expect(xirrTx.date).toEqual(buyTransaction.transactionDate)
  }

  @Test
  fun `should convertToCashFlow for SELL transaction has positive amount`() {
    val sellTransaction = createSellCashFlow(quantity = BigDecimal("10"), price = BigDecimal("120"))

    val xirrTx = xirrCalculationService.convertToCashFlow(sellTransaction)

    expect(xirrTx.amount).toBeGreaterThan(0.0)
    expect(xirrTx.date).toEqual(sellTransaction.transactionDate)
  }

  @ParameterizedTest
  @CsvSource("10, 100, 20, -1020.0", "1, 1000, 500, -1500.0")
  fun `should convertToCashFlow adds commission to the cost of a buy`(
    quantity: BigDecimal,
    price: BigDecimal,
    commission: BigDecimal,
    expectedAmount: Double,
  ) {
    val buyTransaction = createBuyCashFlow(quantity = quantity, price = price, commission = commission)

    val xirrTx = xirrCalculationService.convertToCashFlow(buyTransaction)

    expect(xirrTx.amount).toEqual(expectedAmount)
  }

  @ParameterizedTest
  @CsvSource("10, 100, 15, 985.0", "10, 100, 0, 1000.0")
  fun `should convertToCashFlow subtracts commission from the proceeds of a sell`(
    quantity: BigDecimal,
    price: BigDecimal,
    commission: BigDecimal,
    expectedAmount: Double,
  ) {
    val sellTransaction = createSellCashFlow(quantity = quantity, price = price, commission = commission)

    val xirrTx = xirrCalculationService.convertToCashFlow(sellTransaction)

    expect(xirrTx.amount).toEqual(expectedAmount)
  }

  @Test
  fun `should buildCashFlows with multiple buy and sell transactions`() {
    val transactions =
      listOf(
        createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50"), date = testDate.minusDays(90)),
        createBuyCashFlow(quantity = BigDecimal("50"), price = BigDecimal("60"), date = testDate.minusDays(60)),
        createSellCashFlow(quantity = BigDecimal("30"), price = BigDecimal("70"), date = testDate.minusDays(30)),
        createSellCashFlow(quantity = BigDecimal("20"), price = BigDecimal("80"), date = testDate.minusDays(15)),
      )
    val currentValue = BigDecimal("10000")

    val xirrCashFlows = xirrCalculationService.buildCashFlows(transactions, currentValue, testDate)

    expect(xirrCashFlows).toHaveSize(5)
    expect(xirrCashFlows[0].amount).toBeLessThan(0.0)
    expect(xirrCashFlows[1].amount).toBeLessThan(0.0)
    expect(xirrCashFlows[2].amount).toBeGreaterThan(0.0)
    expect(xirrCashFlows[3].amount).toBeGreaterThan(0.0)
    expect(xirrCashFlows[4].amount).toEqual(10000.0)
  }
}
