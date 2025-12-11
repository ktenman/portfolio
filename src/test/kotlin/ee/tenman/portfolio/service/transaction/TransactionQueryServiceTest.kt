package ee.tenman.portfolio.service.transaction

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.testing.fixture.MetricsFixtures
import ee.tenman.portfolio.testing.fixture.TransactionFixtures
import ee.tenman.portfolio.usecase.GetPortfolioPerformanceUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class TransactionQueryServiceTest {
  private val transactionService = mockk<TransactionService>()
  private val getPortfolioPerformanceUseCase = mockk<GetPortfolioPerformanceUseCase>()
  private lateinit var transactionQueryService: TransactionQueryService

  private val testDate = LocalDate.of(2024, 1, 1)
  private val testInstrument = TransactionFixtures.createInstrument()

  @BeforeEach
  fun setUp() {
    transactionQueryService = TransactionQueryService(transactionService, getPortfolioPerformanceUseCase)
  }

  @Test
  fun `should return transactions with summary without date filter`() {
    val transaction =
      TransactionFixtures.createBuyTransaction(
      testInstrument,
        BigDecimal("10"),
        BigDecimal("100"),
        testDate,
        commission = TransactionFixtures.ZERO_COMMISSION,
    )
    val metrics = MetricsFixtures.createMetrics(unrealizedProfit = BigDecimal("500"))
    every { transactionService.getAllTransactions(null, null, null) } returns listOf(transaction)
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    every { getPortfolioPerformanceUseCase(1L) } returns metrics
    val result = transactionQueryService.getTransactionsWithSummary(null, null, null)
    expect(result.transactions).toHaveSize(1)
    expect(result.summary.totalUnrealizedProfit).toEqualNumerically(BigDecimal("500"))
    verify { transactionService.calculateTransactionProfits(any()) }
  }

  @Test
  fun `should calculate profits with full history when date filtered`() {
    val transaction =
      TransactionFixtures
        .createBuyTransaction(
      testInstrument,
        BigDecimal("10"),
        BigDecimal("100"),
        testDate,
        commission = TransactionFixtures.ZERO_COMMISSION,
    ).apply { id = 1L }
    val fullHistoryTx =
      TransactionFixtures
        .createBuyTransaction(
      testInstrument,
        BigDecimal("10"),
        BigDecimal("100"),
        testDate,
        commission = TransactionFixtures.ZERO_COMMISSION,
    ).apply {
      id = 1L
      realizedProfit = BigDecimal("50")
      unrealizedProfit = BigDecimal("100")
    }
    val metrics = MetricsFixtures.createMetrics(unrealizedProfit = BigDecimal("100"))
    every { transactionService.getAllTransactions(null, LocalDate.of(2024, 1, 1), null) } returns listOf(transaction)
    every { transactionService.getFullTransactionHistoryForProfitCalculation(any(), any()) } returns listOf(fullHistoryTx)
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    every { getPortfolioPerformanceUseCase(1L) } returns metrics
    val result = transactionQueryService.getTransactionsWithSummary(null, LocalDate.of(2024, 1, 1), null)
    expect(result.transactions).toHaveSize(1)
    verify { transactionService.getFullTransactionHistoryForProfitCalculation(any(), any()) }
  }

  @Test
  fun `should calculate total realized profit from sell transactions`() {
    val buyTx =
      TransactionFixtures.createBuyTransaction(
      testInstrument,
        BigDecimal("10"),
        BigDecimal("100"),
        testDate,
        commission = TransactionFixtures.ZERO_COMMISSION,
    )
    val sellTx =
      TransactionFixtures
        .createSellTransaction(
      testInstrument,
        BigDecimal("5"),
        BigDecimal("120"),
        testDate,
        commission = TransactionFixtures.ZERO_COMMISSION,
    ).apply { realizedProfit = BigDecimal("100") }
    val metrics = MetricsFixtures.createMetrics(unrealizedProfit = BigDecimal.ZERO)
    every { transactionService.getAllTransactions(null, null, null) } returns listOf(buyTx, sellTx)
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    every { getPortfolioPerformanceUseCase(1L) } returns metrics
    val result = transactionQueryService.getTransactionsWithSummary(null, null, null)
    expect(result.summary.totalRealizedProfit).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should calculate total invested correctly`() {
    val buyTx = TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("10"), BigDecimal("100"), testDate)
    val sellTx = TransactionFixtures.createSellTransaction(testInstrument, BigDecimal("5"), BigDecimal("120"), testDate)
    val metrics = MetricsFixtures.createMetrics(unrealizedProfit = BigDecimal.ZERO)
    every { transactionService.getAllTransactions(null, null, null) } returns listOf(buyTx, sellTx)
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    every { getPortfolioPerformanceUseCase(1L) } returns metrics
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
    val transaction =
      TransactionFixtures
        .createBuyTransaction(
      testInstrument,
        BigDecimal("10"),
        BigDecimal("100"),
        testDate,
        commission = TransactionFixtures.ZERO_COMMISSION,
    ).apply { id = 1L }
    every { transactionService.getTransactionById(1L) } returns transaction
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    val result = transactionQueryService.getTransactionWithProfits(1L)
    expect(result.instrumentId).toEqual(1L)
    verify { transactionService.calculateTransactionProfits(listOf(transaction)) }
  }

  @Test
  fun `should filter by platforms`() {
    val transaction =
      TransactionFixtures.createBuyTransaction(
      testInstrument,
        BigDecimal("10"),
        BigDecimal("100"),
        testDate,
        commission = TransactionFixtures.ZERO_COMMISSION,
    )
    val metrics = MetricsFixtures.createMetrics(unrealizedProfit = BigDecimal("500"))
    every { transactionService.getAllTransactions(listOf("LHV"), null, null) } returns listOf(transaction)
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    every { getPortfolioPerformanceUseCase(1L) } returns metrics
    val result = transactionQueryService.getTransactionsWithSummary(listOf("LHV"), null, null)
    expect(result.transactions).toHaveSize(1)
    verify { transactionService.getAllTransactions(listOf("LHV"), null, null) }
  }
}
