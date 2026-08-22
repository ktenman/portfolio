package ee.tenman.portfolio.service.calculation

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import ee.tenman.portfolio.testing.fixture.CashFlowTestBase
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class XirrCalculationAdjustedTest : CashFlowTestBase() {
  private val xirrCalculationService = XirrCalculationService(clock)

  @Test
  fun `should calculateAdjustedXirr with sufficient transactions returns bounded value`() {
    val transactions =
      listOf(
        CashFlow(-1000.0, testDate.minusDays(150)),
        CashFlow(-500.0, testDate.minusDays(100)),
        CashFlow(2000.0, testDate),
      )

    val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).notToEqualNull().toBeGreaterThanOrEqualTo(-10.0).toBeLessThanOrEqualTo(10.0)
  }

  @Test
  fun `should return heavily damped bounded value when weighted investment age is below two days`() {
    val transactions =
      listOf(
        CashFlow(-5000.0, testDate.minusDays(2)),
        CashFlow(-5000.0, testDate.minusDays(1)),
        CashFlow(10200.0, testDate),
      )

    val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).notToEqualNull().toBeGreaterThanOrEqualTo(-10.0).toBeLessThanOrEqualTo(10.0)
  }

  @Test
  fun `should calculateAdjustedXirr with fewer than 2 transactions returns null`() {
    val transactions = listOf(CashFlow(-1000.0, testDate))

    val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toEqual(null)
  }

  @Test
  fun `should calculateAdjustedXirr handles empty negative cashflows`() {
    val transactions =
      listOf(
        CashFlow(500.0, testDate.minusDays(50)),
        CashFlow(1000.0, testDate),
      )

    val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toEqual(null)
  }

  @Test
  fun `should calculateAdjustedXirr caps an extreme gain at the upper bound`() {
    val transactions =
      listOf(
        CashFlow(-1000.0, testDate.minusDays(106)),
        CashFlow(100000.0, testDate),
      )

    val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).notToEqualNull().toBeLessThanOrEqualTo(10.0)
  }

  @Test
  fun `should calculateAdjustedXirr caps an extreme loss at the lower bound`() {
    val transactions =
      listOf(
        CashFlow(-10000.0, testDate.minusDays(106)),
        CashFlow(100.0, testDate),
      )

    val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).notToEqualNull().toBeGreaterThanOrEqualTo(-10.0)
  }

  @ParameterizedTest
  @CsvSource("1, 1200.0", "92, 1200.0", "100, 1500.0")
  fun `should calculateAdjustedXirr returns bounded value regardless of investment period`(
    daysHeld: Long,
    proceeds: Double,
  ) {
    val transactions =
      listOf(
        CashFlow(-1000.0, testDate.minusDays(daysHeld)),
        CashFlow(proceeds, testDate),
      )

    val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).notToEqualNull().toBeGreaterThanOrEqualTo(-10.0).toBeLessThanOrEqualTo(10.0)
  }

  @Test
  fun `should calculateAdjustedXirr handles exception and returns null`() {
    val transactions =
      listOf(
        CashFlow(-1000.0, testDate),
        CashFlow(1000.0, testDate),
      )

    val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, testDate)

    expect(xirr).toEqual(null)
  }
}
