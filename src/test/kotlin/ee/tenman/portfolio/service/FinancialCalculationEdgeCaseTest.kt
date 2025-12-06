package ee.tenman.portfolio.service

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
import ee.tenman.portfolio.service.xirr.Transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class FinancialCalculationEdgeCaseTest {
  private lateinit var xirrCalculationService: XirrCalculationService
  private lateinit var holdingsCalculationService: HoldingsCalculationService
  private lateinit var testInstrument: Instrument

  @BeforeEach
  fun setUp() {
    xirrCalculationService = XirrCalculationService()
    holdingsCalculationService = HoldingsCalculationService()
    testInstrument = createTestInstrument()
  }

  @Nested
  inner class SingleTransactionXirrEdgeCases {
    @Test
    fun `should return zero XIRR for single buy transaction`() {
      val transaction = Transaction(-1000.0, LocalDate.now().minusDays(30))
      val xirr = xirrCalculationService.calculateAdjustedXirr(listOf(transaction), LocalDate.now())
      expect(xirr).toEqual(0.0)
    }

    @Test
    fun `should return zero XIRR for single sell transaction`() {
      val transaction = Transaction(1000.0, LocalDate.now().minusDays(30))
      val xirr = xirrCalculationService.calculateAdjustedXirr(listOf(transaction), LocalDate.now())
      expect(xirr).toEqual(0.0)
    }

    @Test
    fun `should handle very recent transaction with small time delta`() {
      val transactions =
        listOf(
        Transaction(-1000.0, LocalDate.now().minusDays(1)),
        Transaction(1010.0, LocalDate.now()),
      )
      val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, LocalDate.now())
      expect(xirr).toBeGreaterThanOrEqualTo(-10.0)
      expect(xirr).toBeLessThanOrEqualTo(10.0)
    }
  }

  @Nested
  inner class NegativeValueEdgeCases {
    @Test
    fun `should handle sell exceeding buy quantity`() {
      val transactions =
        listOf(
        createTransaction(TransactionType.BUY, BigDecimal("10"), BigDecimal("100")),
        createTransaction(TransactionType.SELL, BigDecimal("15"), BigDecimal("120")),
      )
      val netQuantity = holdingsCalculationService.calculateNetQuantity(transactions)
      expect(netQuantity).toEqualNumerically(BigDecimal("-5"))
    }

    @Test
    fun `should calculate zero holdings when all sold`() {
      val transactions =
        listOf(
        createTransaction(TransactionType.BUY, BigDecimal("10"), BigDecimal("100")),
        createTransaction(TransactionType.SELL, BigDecimal("10"), BigDecimal("120")),
      )
      val (quantity, _) = holdingsCalculationService.calculateCurrentHoldings(transactions)
      expect(quantity).toEqualNumerically(BigDecimal.ZERO)
    }

    @Test
    fun `should handle zero price transaction`() {
      val transaction = createTransaction(TransactionType.BUY, BigDecimal("10"), BigDecimal.ZERO)
      val (quantity, averageCost) = holdingsCalculationService.calculateCurrentHoldings(listOf(transaction))
      expect(quantity).toEqualNumerically(BigDecimal("10"))
      expect(averageCost).toEqualNumerically(BigDecimal("0.05"))
    }
  }

  @Nested
  inner class YearBoundaryEdgeCases {
    @Test
    fun `should handle year boundary December 31 to January 1`() {
      val transactions =
        listOf(
        Transaction(-1000.0, LocalDate.of(2023, 12, 31)),
        Transaction(1050.0, LocalDate.of(2024, 1, 1)),
      )
      val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, LocalDate.of(2024, 1, 1))
      expect(xirr).toBeGreaterThanOrEqualTo(-10.0)
      expect(xirr).toBeLessThanOrEqualTo(10.0)
    }

    @Test
    fun `should handle leap year February 29`() {
      val transactions =
        listOf(
        Transaction(-1000.0, LocalDate.of(2024, 2, 28)),
        Transaction(1005.0, LocalDate.of(2024, 2, 29)),
      )
      val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, LocalDate.of(2024, 2, 29))
      expect(xirr).toBeGreaterThanOrEqualTo(-10.0)
      expect(xirr).toBeLessThanOrEqualTo(10.0)
    }
  }

  @Nested
  inner class FarFutureDateEdgeCases {
    @Test
    fun `should handle far future date 2050`() {
      val futureDate = LocalDate.of(2050, 6, 15)
      val transactions =
        listOf(
        Transaction(-1000.0, LocalDate.of(2024, 1, 1)),
        Transaction(5000.0, futureDate),
      )
      val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, futureDate)
      expect(xirr).toBeGreaterThanOrEqualTo(-10.0)
      expect(xirr).toBeLessThanOrEqualTo(10.0)
    }

    @Test
    fun `should handle very long holding period`() {
      val transactions =
        listOf(
        Transaction(-1000.0, LocalDate.of(2000, 1, 1)),
        Transaction(10000.0, LocalDate.of(2024, 12, 31)),
      )
      val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, LocalDate.of(2024, 12, 31))
      expect(xirr).toBeGreaterThanOrEqualTo(-10.0)
      expect(xirr).toBeLessThanOrEqualTo(10.0)
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
      val transaction = createTransaction(TransactionType.BUY, BigDecimal.ZERO, BigDecimal("100"))
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
        createTransaction(TransactionType.BUY, BigDecimal("0.00000001"), BigDecimal("100")),
      )
      val (quantity, _) = holdingsCalculationService.calculateCurrentHoldings(transactions)
      expect(quantity).toEqualNumerically(BigDecimal("0.00000001"))
    }

    @Test
    fun `should handle very large quantities`() {
      val transactions =
        listOf(
        createTransaction(TransactionType.BUY, BigDecimal("999999999.99"), BigDecimal("100")),
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
        createTransaction(TransactionType.BUY, BigDecimal("10"), BigDecimal("100")),
        createTransaction(TransactionType.SELL, BigDecimal("10"), BigDecimal("100")),
      )
      val xirrTransactions =
        xirrCalculationService.buildXirrTransactions(
        transactions,
        BigDecimal.ZERO,
        LocalDate.now(),
      )
      expect(xirrTransactions.any { it.amount > 0 && it.date == LocalDate.now() }).toEqual(false)
    }

    @Test
    fun `should handle commission in transaction conversion`() {
      val buyTransaction =
        PortfolioTransaction(
        instrument = testInstrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        transactionDate = LocalDate.now().minusDays(30),
        platform = Platform.LIGHTYEAR,
        commission = BigDecimal("10"),
      )
      val xirrTransaction = xirrCalculationService.convertToXirrTransaction(buyTransaction)
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
        transactionDate = LocalDate.now().minusDays(30),
        platform = Platform.LIGHTYEAR,
        commission = BigDecimal("10"),
      )
      val xirrTransaction = xirrCalculationService.convertToXirrTransaction(sellTransaction)
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

  private fun createTransaction(
    type: TransactionType,
    quantity: BigDecimal,
    price: BigDecimal,
    date: LocalDate = LocalDate.now().minusDays(30),
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
