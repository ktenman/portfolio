package ee.tenman.portfolio.service.transaction

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.model.metrics.InstrumentMetrics
import ee.tenman.portfolio.service.calculation.InvestmentMetricsService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class TransactionQueryServiceTest {
  private val transactionService = mockk<TransactionService>()
  private val investmentMetricsService = mockk<InvestmentMetricsService>()
  private lateinit var transactionQueryService: TransactionQueryService
  private lateinit var testInstrument: Instrument

  @BeforeEach
  fun setUp() {
    testInstrument =
      Instrument(
        symbol = "AAPL",
        name = "Apple Inc.",
        category = "Stock",
        baseCurrency = "USD",
        currentPrice = BigDecimal("150.00"),
        providerName = ProviderName.FT,
      ).apply { id = 1L }
    transactionQueryService = TransactionQueryService(transactionService, investmentMetricsService)
  }

  @Test
  fun `should return transactions with summary without date filter`() {
    val transaction = createCashFlow(TransactionType.BUY, BigDecimal("10"), BigDecimal("100"))
    val metrics = createMetrics(unrealizedProfit = BigDecimal("500"))
    every { transactionService.getAllTransactions(null, null, null) } returns listOf(transaction)
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    every { investmentMetricsService.calculateInstrumentMetrics(testInstrument, any()) } returns metrics
    val result = transactionQueryService.getTransactionsWithSummary(null, null, null)
    expect(result.transactions).toHaveSize(1)
    expect(result.summary.totalUnrealizedProfit).toEqualNumerically(BigDecimal("500"))
    verify { transactionService.calculateTransactionProfits(any()) }
  }

  @Test
  fun `should calculate profits with full history when date filtered`() {
    val transaction = createCashFlow(TransactionType.BUY, BigDecimal("10"), BigDecimal("100"))
    transaction.id = 1L
    val fullHistoryTx = createCashFlow(TransactionType.BUY, BigDecimal("10"), BigDecimal("100"))
    fullHistoryTx.id = 1L
    fullHistoryTx.realizedProfit = BigDecimal("50")
    fullHistoryTx.unrealizedProfit = BigDecimal("100")
    val metrics = createMetrics(unrealizedProfit = BigDecimal("100"))
    every { transactionService.getAllTransactions(null, LocalDate.of(2024, 1, 1), null) } returns listOf(transaction)
    every { transactionService.getFullTransactionHistoryForProfitCalculation(any(), any()) } returns listOf(fullHistoryTx)
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    every { investmentMetricsService.calculateInstrumentMetrics(testInstrument, any()) } returns metrics
    val result = transactionQueryService.getTransactionsWithSummary(null, LocalDate.of(2024, 1, 1), null)
    expect(result.transactions).toHaveSize(1)
    verify { transactionService.getFullTransactionHistoryForProfitCalculation(any(), any()) }
  }

  @Test
  fun `should calculate total realized profit from sell transactions`() {
    val buyTx = createCashFlow(TransactionType.BUY, BigDecimal("10"), BigDecimal("100"))
    val sellTx = createCashFlow(TransactionType.SELL, BigDecimal("5"), BigDecimal("120"))
    sellTx.realizedProfit = BigDecimal("100")
    val metrics = createMetrics(unrealizedProfit = BigDecimal.ZERO)
    every { transactionService.getAllTransactions(null, null, null) } returns listOf(buyTx, sellTx)
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    every { investmentMetricsService.calculateInstrumentMetrics(testInstrument, any()) } returns metrics
    val result = transactionQueryService.getTransactionsWithSummary(null, null, null)
    expect(result.summary.totalRealizedProfit).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should calculate total invested correctly`() {
    val buyTx = createCashFlow(TransactionType.BUY, BigDecimal("10"), BigDecimal("100"))
    buyTx.commission = BigDecimal("5")
    val sellTx = createCashFlow(TransactionType.SELL, BigDecimal("5"), BigDecimal("120"))
    sellTx.commission = BigDecimal("5")
    val metrics = createMetrics(unrealizedProfit = BigDecimal.ZERO)
    every { transactionService.getAllTransactions(null, null, null) } returns listOf(buyTx, sellTx)
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    every { investmentMetricsService.calculateInstrumentMetrics(testInstrument, any()) } returns metrics
    val result = transactionQueryService.getTransactionsWithSummary(null, null, null)
    expect(result.summary.totalInvested).toEqualNumerically(BigDecimal("400"))
  }

  @Test
  fun `should return empty transactions when none exist`() {
    every { transactionService.getAllTransactions(null, null, null) } returns emptyList()
    val result = transactionQueryService.getTransactionsWithSummary(null, null, null)
    expect(result.transactions).toHaveSize(0)
    expect(result.summary.totalProfit).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should get single transaction with profits`() {
    val transaction = createCashFlow(TransactionType.BUY, BigDecimal("10"), BigDecimal("100"))
    transaction.id = 1L
    every { transactionService.getTransactionById(1L) } returns transaction
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    val result = transactionQueryService.getTransactionWithProfits(1L)
    expect(result.instrumentId).toEqual(1L)
    verify { transactionService.calculateTransactionProfits(listOf(transaction)) }
  }

  @Test
  fun `should filter by platforms`() {
    val transaction = createCashFlow(TransactionType.BUY, BigDecimal("10"), BigDecimal("100"))
    val metrics = createMetrics(unrealizedProfit = BigDecimal("500"))
    every { transactionService.getAllTransactions(listOf("LHV"), null, null) } returns listOf(transaction)
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    every { investmentMetricsService.calculateInstrumentMetrics(testInstrument, any()) } returns metrics
    val result = transactionQueryService.getTransactionsWithSummary(listOf("LHV"), null, null)
    expect(result.transactions).toHaveSize(1)
    verify { transactionService.getAllTransactions(listOf("LHV"), null, null) }
  }

  private fun createCashFlow(
    type: TransactionType,
    quantity: BigDecimal,
    price: BigDecimal,
  ): PortfolioTransaction =
    PortfolioTransaction(
      instrument = testInstrument,
      transactionType = type,
      quantity = quantity,
      price = price,
      transactionDate = LocalDate.of(2024, 1, 1),
      platform = Platform.LHV,
      commission = BigDecimal.ZERO,
    )

  private fun createMetrics(unrealizedProfit: BigDecimal): InstrumentMetrics =
    InstrumentMetrics(
      totalInvestment = BigDecimal("1000"),
      currentValue = BigDecimal("1500"),
      profit = BigDecimal("500"),
      realizedProfit = BigDecimal.ZERO,
      unrealizedProfit = unrealizedProfit,
      xirr = 25.0,
      quantity = BigDecimal("10"),
    )
}
