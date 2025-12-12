package ee.tenman.portfolio.service.transaction

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.testing.fixture.TransactionFixtures
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class TransactionQueryServiceTest {
  private val transactionService = mockk<TransactionService>()
  private lateinit var transactionQueryService: TransactionQueryService

  private val testDate = LocalDate.of(2024, 1, 1)
  private val testInstrument = TransactionFixtures.createInstrument()

  @BeforeEach
  fun setUp() {
    transactionQueryService = TransactionQueryService(transactionService)
  }

  @Test
  fun `should return transactions with summary without date filter`() {
    val transaction =
      TransactionFixtures
        .createBuyTransaction(
          testInstrument,
          BigDecimal("10"),
          BigDecimal("100"),
          testDate,
          commission = TransactionFixtures.ZERO_COMMISSION,
        ).apply {
          unrealizedProfit = BigDecimal("500")
          remainingQuantity = BigDecimal("10")
        }
    every { transactionService.getAllTransactions(null, null, null) } returns listOf(transaction)
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
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
        ).apply {
          id = 1L
          remainingQuantity = BigDecimal("10")
        }
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
          remainingQuantity = BigDecimal("10")
        }
    every { transactionService.getAllTransactions(null, LocalDate.of(2024, 1, 1), null) } returns listOf(transaction)
    every { transactionService.getFullTransactionHistoryForProfitCalculation(any(), any()) } returns listOf(fullHistoryTx)
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    val result = transactionQueryService.getTransactionsWithSummary(null, LocalDate.of(2024, 1, 1), null)
    expect(result.transactions).toHaveSize(1)
    expect(result.summary.totalUnrealizedProfit).toEqualNumerically(BigDecimal("100"))
    verify { transactionService.getFullTransactionHistoryForProfitCalculation(any(), any()) }
  }

  @Test
  fun `should calculate total realized profit from sell transactions`() {
    val buyTx =
      TransactionFixtures
        .createBuyTransaction(
          testInstrument,
          BigDecimal("10"),
          BigDecimal("100"),
          testDate,
          commission = TransactionFixtures.ZERO_COMMISSION,
        ).apply { remainingQuantity = BigDecimal("5") }
    val sellTx =
      TransactionFixtures
        .createSellTransaction(
          testInstrument,
          BigDecimal("5"),
          BigDecimal("120"),
          testDate,
          commission = TransactionFixtures.ZERO_COMMISSION,
        ).apply { realizedProfit = BigDecimal("100") }
    every { transactionService.getAllTransactions(null, null, null) } returns listOf(buyTx, sellTx)
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    val result = transactionQueryService.getTransactionsWithSummary(null, null, null)
    expect(result.summary.totalRealizedProfit).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should calculate total invested using remaining quantity from profit calculation`() {
    val buyTx =
      TransactionFixtures
        .createBuyTransaction(testInstrument, BigDecimal("10"), BigDecimal("100"), testDate)
        .apply { remainingQuantity = BigDecimal("5") }
    val sellTx =
      TransactionFixtures.createSellTransaction(
        testInstrument,
        BigDecimal("5"),
        BigDecimal("120"),
        testDate.plusDays(1),
      )
    every { transactionService.getAllTransactions(null, null, null) } returns listOf(buyTx, sellTx)
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    val result = transactionQueryService.getTransactionsWithSummary(null, null, null)
    expect(result.summary.totalInvested).toEqualNumerically(BigDecimal("502.5"))
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
        ).apply {
          id = 1L
          remainingQuantity = BigDecimal("10")
        }
    every { transactionService.getTransactionById(1L) } returns transaction
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    val result = transactionQueryService.getTransactionWithProfits(1L)
    expect(result.instrumentId).toEqual(1L)
    verify { transactionService.calculateTransactionProfits(listOf(transaction)) }
  }

  @Test
  fun `should filter by platforms`() {
    val transaction =
      TransactionFixtures
        .createBuyTransaction(
          testInstrument,
          BigDecimal("10"),
          BigDecimal("100"),
          testDate,
          commission = TransactionFixtures.ZERO_COMMISSION,
        ).apply {
          unrealizedProfit = BigDecimal("500")
          remainingQuantity = BigDecimal("10")
        }
    every { transactionService.getAllTransactions(listOf("LHV"), null, null) } returns listOf(transaction)
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    val result = transactionQueryService.getTransactionsWithSummary(listOf("LHV"), null, null)
    expect(result.transactions).toHaveSize(1)
    expect(result.summary.totalUnrealizedProfit).toEqualNumerically(BigDecimal("500"))
    verify { transactionService.getAllTransactions(listOf("LHV"), null, null) }
  }

  @Test
  fun `should calculate unrealized profit only from filtered platform transactions`() {
    val lhvTransaction =
      TransactionFixtures
        .createBuyTransaction(
          testInstrument,
          BigDecimal("1"),
          BigDecimal("14.25"),
          testDate,
          commission = TransactionFixtures.ZERO_COMMISSION,
        ).apply {
          unrealizedProfit = BigDecimal("9.02")
          remainingQuantity = BigDecimal("1")
        }
    every { transactionService.getAllTransactions(listOf("LHV"), null, null) } returns listOf(lhvTransaction)
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    val result = transactionQueryService.getTransactionsWithSummary(listOf("LHV"), null, null)
    expect(result.summary.totalUnrealizedProfit).toEqualNumerically(BigDecimal("9.02"))
  }

  @Test
  fun `should calculate total invested from multiple buys with remaining quantities`() {
    val firstBuy =
      TransactionFixtures
        .createBuyTransaction(testInstrument, BigDecimal("10"), BigDecimal("100"), testDate)
        .apply { remainingQuantity = BigDecimal.ZERO }
    val secondBuy =
      TransactionFixtures
        .createBuyTransaction(testInstrument, BigDecimal("20"), BigDecimal("50"), testDate.plusDays(1))
        .apply { remainingQuantity = BigDecimal("15") }
    val sell =
      TransactionFixtures.createSellTransaction(
        testInstrument,
        BigDecimal("15"),
        BigDecimal("120"),
        testDate.plusDays(2),
      )
    every { transactionService.getAllTransactions(null, null, null) } returns listOf(firstBuy, secondBuy, sell)
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    val result = transactionQueryService.getTransactionsWithSummary(null, null, null)
    expect(result.summary.totalInvested).toEqualNumerically(BigDecimal("1005"))
  }

  @Test
  fun `should calculate total invested when sells from previous position precede new buy`() {
    val oldSell1 =
      TransactionFixtures
        .createSellTransaction(testInstrument, BigDecimal("0.00317"), BigDecimal("62724.95"), testDate)
        .apply { realizedProfit = BigDecimal("50") }
    val oldSell2 =
      TransactionFixtures
        .createSellTransaction(testInstrument, BigDecimal("0.0061"), BigDecimal("65399.82"), testDate.plusDays(12))
        .apply { realizedProfit = BigDecimal("80") }
    val newBuy =
      TransactionFixtures
        .createBuyTransaction(
          testInstrument,
          BigDecimal("0.00927"),
          BigDecimal("92764.78"),
          testDate.plusDays(75),
          commission = TransactionFixtures.ZERO_COMMISSION,
        ).apply { remainingQuantity = BigDecimal("0.00927") }
    every { transactionService.getAllTransactions(listOf("BINANCE"), null, null) } returns
      listOf(oldSell1, oldSell2, newBuy)
    every { transactionService.calculateTransactionProfits(any()) } returns Unit
    val result = transactionQueryService.getTransactionsWithSummary(listOf("BINANCE"), null, null)
    expect(result.summary.totalInvested).toEqualNumerically(BigDecimal("859.9295106"))
  }
}
