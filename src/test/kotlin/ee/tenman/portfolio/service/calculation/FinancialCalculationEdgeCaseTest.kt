package ee.tenman.portfolio.service.calculation

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FinancialCalculationEdgeCaseTest {
  private val clock = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneId.of("UTC"))
  private lateinit var xirrCalculationService: XirrCalculationService
  private lateinit var holdingsCalculationService: HoldingsCalculationService
  private lateinit var testInstrument: Instrument

  @BeforeEach
  fun setUp() {
    xirrCalculationService = XirrCalculationService(clock)
    holdingsCalculationService = HoldingsCalculationService()
    testInstrument = createTestInstrument()
  }

  @Nested
  inner class SingleTransactionXirrEdgeCases {
    @Test
    fun `should return null XIRR for single buy transaction`() {
      val transaction = CashFlow(-1000.0, LocalDate.now(clock).minusDays(30))
      val xirr = xirrCalculationService.calculateAdjustedXirr(listOf(transaction), LocalDate.now(clock))
      expect(xirr).toEqual(null)
    }

    @Test
    fun `should return null XIRR for single sell transaction`() {
      val transaction = CashFlow(1000.0, LocalDate.now(clock).minusDays(30))
      val xirr = xirrCalculationService.calculateAdjustedXirr(listOf(transaction), LocalDate.now(clock))
      expect(xirr).toEqual(null)
    }

    @Test
    fun `should return null for very recent transaction below 30 days`() {
      val transactions =
        listOf(
        CashFlow(-1000.0, LocalDate.now(clock).minusDays(1)),
        CashFlow(1010.0, LocalDate.now(clock)),
      )
      val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, LocalDate.now(clock))
      expect(xirr).toEqual(null)
    }
  }

  @Nested
  inner class NegativeValueEdgeCases {
    @Test
    fun `should handle sell exceeding buy quantity`() {
      val transactions =
        listOf(
        createCashFlow(TransactionType.BUY, BigDecimal("10"), BigDecimal("100")),
        createCashFlow(TransactionType.SELL, BigDecimal("15"), BigDecimal("120")),
      )
      val netQuantity = holdingsCalculationService.calculateNetQuantity(transactions)
      expect(netQuantity).toEqualNumerically(BigDecimal("-5"))
    }

    @Test
    fun `should calculate zero holdings when all sold`() {
      val transactions =
        listOf(
        createCashFlow(TransactionType.BUY, BigDecimal("10"), BigDecimal("100")),
        createCashFlow(TransactionType.SELL, BigDecimal("10"), BigDecimal("120")),
      )
      val (quantity, _) = holdingsCalculationService.calculateCurrentHoldings(transactions)
      expect(quantity).toEqualNumerically(BigDecimal.ZERO)
    }

    @Test
    fun `should handle zero price transaction`() {
      val transaction = createCashFlow(TransactionType.BUY, BigDecimal("10"), BigDecimal.ZERO)
      val (quantity, averageCost) = holdingsCalculationService.calculateCurrentHoldings(listOf(transaction))
      expect(quantity).toEqualNumerically(BigDecimal("10"))
      expect(averageCost).toEqualNumerically(BigDecimal.ZERO)
    }
  }

  @Nested
  inner class YearBoundaryEdgeCases {
    @Test
    fun `should return null for year boundary with 1 day holding`() {
      val transactions =
        listOf(
        CashFlow(-1000.0, LocalDate.of(2023, 12, 31)),
        CashFlow(1050.0, LocalDate.of(2024, 1, 1)),
      )
      val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, LocalDate.of(2024, 1, 1))
      expect(xirr).toEqual(null)
    }

    @Test
    fun `should return null for leap year with 1 day holding`() {
      val transactions =
        listOf(
        CashFlow(-1000.0, LocalDate.of(2024, 2, 28)),
        CashFlow(1005.0, LocalDate.of(2024, 2, 29)),
      )
      val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, LocalDate.of(2024, 2, 29))
      expect(xirr).toEqual(null)
    }
  }

  @Nested
  inner class FarFutureDateEdgeCases {
    @Test
    fun `should handle far future date 2050`() {
      val futureDate = LocalDate.of(2050, 6, 15)
      val transactions =
        listOf(
        CashFlow(-1000.0, LocalDate.of(2024, 1, 1)),
        CashFlow(5000.0, futureDate),
      )
      val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, futureDate)
      expect(xirr).notToEqualNull().toBeGreaterThanOrEqualTo(-10.0).toBeLessThanOrEqualTo(10.0)
    }

    @Test
    fun `should handle very long holding period`() {
      val transactions =
        listOf(
        CashFlow(-1000.0, LocalDate.of(2000, 1, 1)),
        CashFlow(10000.0, LocalDate.of(2024, 12, 31)),
      )
      val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, LocalDate.of(2024, 12, 31))
      expect(xirr).notToEqualNull().toBeGreaterThanOrEqualTo(-10.0).toBeLessThanOrEqualTo(10.0)
    }
  }

  @Nested
  inner class ZeroQuantityEdgeCases {
    @Test
    fun `should handle empty transaction list for holdings`() {
      val (quantity, averageCost) = holdingsCalculationService.calculateCurrentHoldings(emptyList())
      expect(quantity).toEqualNumerically(BigDecimal.ZERO)
      expect(averageCost).toEqualNumerically(BigDecimal.ZERO)
    }

    @Test
    fun `should handle zero quantity buy transaction`() {
      val transaction = createCashFlow(TransactionType.BUY, BigDecimal.ZERO, BigDecimal("100"))
      val (quantity, _) = holdingsCalculationService.calculateCurrentHoldings(listOf(transaction))
      expect(quantity).toEqualNumerically(BigDecimal.ZERO)
    }

    @Test
    fun `should calculate profit correctly when holdings become zero`() {
      val profit =
        holdingsCalculationService.calculateProfit(
        BigDecimal.ZERO,
        BigDecimal("100"),
        BigDecimal("150"),
      )
      expect(profit).toEqualNumerically(BigDecimal.ZERO)
    }
  }

  @Nested
  inner class PrecisionEdgeCases {
    @Test
    fun `should handle very small quantities`() {
      val transactions =
        listOf(
        createCashFlow(TransactionType.BUY, BigDecimal("0.00000001"), BigDecimal("100")),
      )
      val (quantity, _) = holdingsCalculationService.calculateCurrentHoldings(transactions)
      expect(quantity).toEqualNumerically(BigDecimal("0.00000001"))
    }

    @Test
    fun `should handle very large quantities`() {
      val transactions =
        listOf(
        createCashFlow(TransactionType.BUY, BigDecimal("999999999.99"), BigDecimal("100")),
      )
      val (quantity, _) = holdingsCalculationService.calculateCurrentHoldings(transactions)
      expect(quantity).toEqualNumerically(BigDecimal("999999999.99"))
    }

    @Test
    fun `should handle very large prices`() {
      val currentValue =
        holdingsCalculationService.calculateCurrentValue(
        BigDecimal("1"),
        BigDecimal("999999999999.99"),
      )
      expect(currentValue).toEqualNumerically(BigDecimal("999999999999.99"))
    }
  }

  @Nested
  inner class XirrTransactionBuildingEdgeCases {
    @Test
    fun `should build empty XIRR transaction list when current value is zero`() {
      val transactions =
        listOf(
        createCashFlow(TransactionType.BUY, BigDecimal("10"), BigDecimal("100")),
        createCashFlow(TransactionType.SELL, BigDecimal("10"), BigDecimal("100")),
      )
      val xirrCashFlows =
        xirrCalculationService.buildCashFlows(
        transactions,
        BigDecimal.ZERO,
        LocalDate.now(clock),
      )
      expect(xirrCashFlows.any { it.amount > 0 && it.date == LocalDate.now(clock) }).toEqual(false)
    }

    @Test
    fun `should handle commission in transaction conversion`() {
      val buyTransaction =
        PortfolioTransaction(
        instrument = testInstrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        transactionDate = LocalDate.now(clock).minusDays(30),
        platform = Platform.LIGHTYEAR,
        commission = BigDecimal("10"),
      )
      val xirrTransaction = xirrCalculationService.convertToCashFlow(buyTransaction)
      expect(xirrTransaction.amount).toEqual(-1010.0)
    }

    @Test
    fun `should subtract commission from sell transaction`() {
      val sellTransaction =
        PortfolioTransaction(
        instrument = testInstrument,
        transactionType = TransactionType.SELL,
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        transactionDate = LocalDate.now(clock).minusDays(30),
        platform = Platform.LIGHTYEAR,
        commission = BigDecimal("10"),
      )
      val xirrTransaction = xirrCalculationService.convertToCashFlow(sellTransaction)
      expect(xirrTransaction.amount).toEqual(990.0)
    }
  }

  private fun createTestInstrument(): Instrument =
    Instrument(
      symbol = "TEST",
      name = "Test Instrument",
      category = "Stock",
      baseCurrency = "EUR",
      currentPrice = BigDecimal("100.00"),
      providerName = ProviderName.FT,
    ).apply { id = 1L }

  private fun createCashFlow(
    type: TransactionType,
    quantity: BigDecimal,
    price: BigDecimal,
    date: LocalDate = LocalDate.now(clock).minusDays(30),
  ): PortfolioTransaction =
    PortfolioTransaction(
      instrument = testInstrument,
      transactionType = type,
      quantity = quantity,
      price = price,
      transactionDate = date,
      platform = Platform.LIGHTYEAR,
      commission = BigDecimal("0.50"),
    )
}
