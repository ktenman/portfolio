package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import ee.tenman.portfolio.testing.fixture.TransactionFixtures
import io.mockk.every
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class SummaryServiceCalculationTest : SummaryServiceTestBase() {
  @Test
  fun `calculateSummaryForDate should return zero values when no transactions exist`() {
    val date = LocalDate.of(2024, 7, 1)
    every { transactionService.getAllTransactions() } returns emptyList()
    every { summaryCacheService.findByEntryDate(any()) } returns null

    val summary = summaryService.calculateSummaryForDate(date)

    expect(summary.entryDate).toEqual(date)
    expect(summary.totalValue).toEqualNumerically(BigDecimal.ZERO)
    expect(summary.xirrAnnualReturn).toEqualNumerically(BigDecimal.ZERO)
    expect(summary.totalProfit).toEqualNumerically(BigDecimal.ZERO)
    expect(summary.earningsPerDay).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `calculateSummaryForDate should fall back to legacy calculation when unified service fails`() {
    val date = LocalDate.of(2024, 7, 1)
    val price = BigDecimal("123.45")
    val quantity = BigDecimal("10")
    val originalPrice = BigDecimal("100")
    val testTransaction = createBuyTransaction(quantity, originalPrice, date.minusDays(10))

    every { transactionService.getAllTransactions() } returns listOf(testTransaction)
    every { xirrCalculationService.calculateAdjustedXirr(any(), date) } returns 0.05

    val expectedTotal = price.multiply(quantity)
    val expectedProfit = expectedTotal.subtract(originalPrice.multiply(quantity))

    stubMetrics(
      date,
      expectedTotal,
      expectedProfit,
      CashFlow(-1000.0, date.minusDays(10)),
      CashFlow(expectedTotal.toDouble(), date),
    )

    val summary = summaryService.calculateSummaryForDate(date)

    expect(summary.totalValue).toEqualNumerically(expectedTotal)
    expect(summary.totalProfit).toEqualNumerically(expectedProfit)
    expect(summary.earningsPerDay).toEqualNumerically(expectedEarningsPerDay(expectedTotal, BigDecimal("0.05")))
  }

  @Test
  fun `calculateSummaryForDate should use hardcoded profit for known problematic value`() {
    val date = LocalDate.of(2024, 7, 1)
    val price = BigDecimal("31.5448")
    val quantity = BigDecimal("793.00")

    val testTransaction = createBuyTransaction(quantity, BigDecimal("29.81"), date.minusDays(10))

    every { transactionService.getAllTransactions() } returns listOf(testTransaction)
    every { xirrCalculationService.calculateAdjustedXirr(any(), date) } returns 0.05

    val expectedTotalValue = price.multiply(quantity)
    stubMetrics(
      date,
      expectedTotalValue,
      BigDecimal.ZERO,
      CashFlow(-23639.33, date.minusDays(10)),
      CashFlow(expectedTotalValue.toDouble(), date),
    )

    val summary = summaryService.calculateSummaryForDate(date)

    expect(summary.totalValue.setScale(2, RoundingMode.HALF_UP)).toEqualNumerically(BigDecimal("25015.03"))
    expect(summary.totalProfit).toEqualNumerically(BigDecimal("0E-10"))
    expect(summary.earningsPerDay).toEqualNumerically(expectedEarningsPerDay(summary.totalValue, summary.xirrAnnualReturn))
  }

  @ParameterizedTest
  @CsvSource("1525.00, 125.00, 0.06", "1400.00, 0.00, 0.03", "1575.00, 175.00, 0.08")
  fun `calculateSummaryForDate should correctly aggregate multiple instruments`(
    expectedTotalValue: BigDecimal,
    expectedTotalProfit: BigDecimal,
    xirrValue: Double,
  ) {
    val date = LocalDate.of(2024, 7, 1)

    val otherInstrument =
      TransactionFixtures.createInstrument(
        symbol = "VUSA:LON:GBP",
        name = "Vanguard S&P 500 UCITS ETF",
        category = "ETF",
        baseCurrency = "GBP",
        currentPrice = BigDecimal("85.76"),
        id = 2L,
      )

    val transaction1 = createBuyTransaction(BigDecimal("10"), BigDecimal("100"), date.minusDays(10))
    val transaction2 = createBuyTransaction(BigDecimal("5"), BigDecimal("80"), date.minusDays(5), instrument = otherInstrument)

    every { transactionService.getAllTransactions() } returns listOf(transaction1, transaction2)

    every { xirrCalculationService.calculateAdjustedXirr(any(), date) } returns xirrValue

    stubMetrics(
      date,
      expectedTotalValue,
      expectedTotalProfit,
      CashFlow(-1400.0, date.minusDays(10)),
      CashFlow(expectedTotalValue.toDouble(), date),
    )

    val summary = summaryService.calculateSummaryForDate(date)

    expect(summary.totalValue.setScale(2, RoundingMode.HALF_UP)).toEqualNumerically(expectedTotalValue)
    expect(summary.totalProfit.setScale(2, RoundingMode.HALF_UP)).toEqualNumerically(expectedTotalProfit)
    expect(summary.xirrAnnualReturn).toEqualNumerically(BigDecimal(xirrValue).setScale(8, RoundingMode.HALF_UP))
  }

  @Test
  fun `calculateSummaryForDate should handle SELL transactions`() {
    val date = LocalDate.of(2024, 7, 10)

    val buyTransaction = createBuyTransaction(BigDecimal("20"), BigDecimal("100"), date.minusDays(15))
    val sellTransaction =
      TransactionFixtures.createSellTransaction(
        instrument = instrument,
        quantity = BigDecimal("8"),
        price = BigDecimal("120"),
        transactionDate = date.minusDays(5),
        platform = Platform.TRADING212,
        commission = TransactionFixtures.ZERO_COMMISSION,
      )

    every { transactionService.getAllTransactions() } returns listOf(buyTransaction, sellTransaction)

    every { xirrCalculationService.calculateAdjustedXirr(any(), date) } returns 0.12

    val expectedTotalValue = BigDecimal("1560.00")
    val expectedTotalProfit = BigDecimal("360.00")

    stubMetrics(
      date,
      expectedTotalValue,
      expectedTotalProfit,
      CashFlow(-2000.0, date.minusDays(15)),
      CashFlow(960.0, date.minusDays(5)),
      CashFlow(expectedTotalValue.toDouble(), date),
    )

    val summary = summaryService.calculateSummaryForDate(date)

    expect(summary.totalValue.setScale(2, RoundingMode.HALF_UP)).toEqualNumerically(expectedTotalValue)
    expect(summary.totalProfit.setScale(2, RoundingMode.HALF_UP)).toEqualNumerically(expectedTotalProfit)
    expect(summary.xirrAnnualReturn).toEqualNumerically(BigDecimal("0.12000000"))
  }
}
