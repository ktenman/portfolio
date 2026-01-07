package ee.tenman.portfolio.service.transaction

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.exception.EntityNotFoundException
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.calculation.ProfitCalculationEngine
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional

class TransactionServiceRetryTest {
  private lateinit var portfolioTransactionRepository: PortfolioTransactionRepository
  private lateinit var profitCalculationEngine: ProfitCalculationEngine
  private lateinit var transactionCacheService: TransactionCacheService
  private lateinit var transactionService: TransactionService
  private lateinit var testInstrument: Instrument
  private val testDate = LocalDate.of(2024, 1, 15)
  private val clock = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneId.of("UTC"))

  @BeforeEach
  fun setUp() {
    portfolioTransactionRepository = mockk()
    profitCalculationEngine = ProfitCalculationEngine()
    transactionCacheService = mockk()
    transactionService =
      TransactionService(
      portfolioTransactionRepository,
      profitCalculationEngine,
      transactionCacheService,
      clock,
    )
    testInstrument =
      Instrument(
      symbol = "AAPL",
      name = "Apple Inc.",
      category = "Stock",
      baseCurrency = "USD",
      currentPrice = BigDecimal("150.00"),
      providerName = ProviderName.FT,
    ).apply { id = 1L }
  }

  @Nested
  inner class ErrorPathTests {
    @Test
    fun `should throw EntityNotFoundException when transaction not found by id`() {
      every { portfolioTransactionRepository.findById(999L) } returns Optional.empty()

      expect { transactionService.getTransactionById(999L) }
        .toThrow<EntityNotFoundException>()
        .messageToContain("Transaction not found with id: 999")
    }

    @Test
    fun `should propagate optimistic locking failure on save after retries exhausted`() {
      val transaction = createTransaction()
      every { portfolioTransactionRepository.save(any()) } throws
        ObjectOptimisticLockingFailureException(PortfolioTransaction::class.java, 1L)

      expect { transactionService.saveTransaction(transaction) }
        .toThrow<ObjectOptimisticLockingFailureException>()
    }

    @Test
    fun `should propagate optimistic locking failure on delete after retries exhausted`() {
      every { portfolioTransactionRepository.deleteById(1L) } throws
        ObjectOptimisticLockingFailureException(PortfolioTransaction::class.java, 1L)

      expect { transactionService.deleteTransaction(1L) }
        .toThrow<ObjectOptimisticLockingFailureException>()
    }

    @Test
    fun `should return empty list for invalid platform name`() {
      val result = transactionService.getAllTransactions(listOf("INVALID_PLATFORM"))

      expect(result).toEqual(emptyList())
    }

    @Test
    fun `should return empty list for empty platform list`() {
      every { transactionCacheService.getAllTransactions() } returns emptyList()

      val result = transactionService.getAllTransactions(platforms = emptyList())

      expect(result).toEqual(emptyList())
    }
  }

  @Nested
  inner class CalculationErrorPaths {
    @Test
    fun `should handle empty transaction list gracefully`() {
      transactionService.calculateTransactionProfits(emptyList())
    }

    @Test
    fun `should handle transaction with null instrument price`() {
      testInstrument.currentPrice = null
      val transaction = createTransaction()

      transactionService.calculateTransactionProfits(listOf(transaction))

      expect(transaction.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
    }

    @Test
    fun `should handle transaction with zero quantity`() {
      val transaction = createTransaction(quantity = BigDecimal.ZERO)

      transactionService.calculateTransactionProfits(listOf(transaction))

      expect(transaction.realizedProfit).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
    }
  }

  private fun createTransaction(
    quantity: BigDecimal = BigDecimal("10"),
    price: BigDecimal = BigDecimal("100"),
  ): PortfolioTransaction =
    PortfolioTransaction(
    instrument = testInstrument,
    transactionType = TransactionType.BUY,
    quantity = quantity,
    price = price,
    transactionDate = testDate,
    platform = Platform.LHV,
    commission = BigDecimal("5"),
  )
}
