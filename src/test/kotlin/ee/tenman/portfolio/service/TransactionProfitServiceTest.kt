package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

class TransactionProfitServiceTest {
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val portfolioTransactionRepository = mockk<PortfolioTransactionRepository>()

  private lateinit var transactionProfitService: TransactionProfitService
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

    transactionProfitService = TransactionProfitService(instrumentRepository, portfolioTransactionRepository)
  }

  @Test
  fun `should not recalculate when instrument not found`() {
    every { instrumentRepository.findById(999L) } returns Optional.empty()

    transactionProfitService.recalculateProfitsForInstrument(999L)

    verify(exactly = 0) { portfolioTransactionRepository.findAllByInstrumentId(any()) }
  }

  @Test
  fun `should not recalculate when no transactions exist`() {
    every { instrumentRepository.findById(1L) } returns Optional.of(testInstrument)
    every { portfolioTransactionRepository.findAllByInstrumentId(1L) } returns emptyList()

    transactionProfitService.recalculateProfitsForInstrument(1L)

    verify(exactly = 0) { portfolioTransactionRepository.saveAll(any<List<PortfolioTransaction>>()) }
  }

  @Test
  fun `should calculate realized profit for sell transaction`() {
    val buyTx = createTransaction(TransactionType.BUY, BigDecimal("10"), BigDecimal("100"), LocalDate.of(2024, 1, 1))
    val sellTx = createTransaction(TransactionType.SELL, BigDecimal("5"), BigDecimal("120"), LocalDate.of(2024, 1, 10))

    val transactionsSlot = slot<List<PortfolioTransaction>>()

    every { instrumentRepository.findById(1L) } returns Optional.of(testInstrument)
    every { portfolioTransactionRepository.findAllByInstrumentId(1L) } returns listOf(buyTx, sellTx)
    every { portfolioTransactionRepository.saveAll(capture(transactionsSlot)) } answers { transactionsSlot.captured }

    transactionProfitService.recalculateProfitsForInstrument(1L)

    val savedSellTx = transactionsSlot.captured.find { it.transactionType == TransactionType.SELL }!!
    expect(savedSellTx.realizedProfit).notToEqualNull().toEqualNumerically(BigDecimal("92.50"))
  }

  @Test
  fun `should calculate unrealized profit for buy transaction`() {
    val buyTx = createTransaction(TransactionType.BUY, BigDecimal("10"), BigDecimal("100"), LocalDate.of(2024, 1, 1))

    val transactionsSlot = slot<List<PortfolioTransaction>>()

    every { instrumentRepository.findById(1L) } returns Optional.of(testInstrument)
    every { portfolioTransactionRepository.findAllByInstrumentId(1L) } returns listOf(buyTx)
    every { portfolioTransactionRepository.saveAll(capture(transactionsSlot)) } answers { transactionsSlot.captured }

    transactionProfitService.recalculateProfitsForInstrument(1L)

    val savedBuyTx = transactionsSlot.captured.find { it.transactionType == TransactionType.BUY }!!
    expect(savedBuyTx.unrealizedProfit).toEqualNumerically(BigDecimal("495.00"))
    expect(savedBuyTx.remainingQuantity).toEqualNumerically(BigDecimal("10"))
  }

  @Test
  fun `should handle multiple platforms separately`() {
    val lhvBuy = createTransaction(TransactionType.BUY, BigDecimal("10"), BigDecimal("100"), LocalDate.of(2024, 1, 1), Platform.LHV)
    val lightyearBuy =
      createTransaction(TransactionType.BUY, BigDecimal("5"), BigDecimal("110"), LocalDate.of(2024, 1, 1), Platform.LIGHTYEAR)

    val transactionsSlot = slot<List<PortfolioTransaction>>()

    every { instrumentRepository.findById(1L) } returns Optional.of(testInstrument)
    every { portfolioTransactionRepository.findAllByInstrumentId(1L) } returns listOf(lhvBuy, lightyearBuy)
    every { portfolioTransactionRepository.saveAll(capture(transactionsSlot)) } answers { transactionsSlot.captured }

    transactionProfitService.recalculateProfitsForInstrument(1L)

    verify { portfolioTransactionRepository.saveAll(any<List<PortfolioTransaction>>()) }
  }

  @Test
  fun `should set zero remaining quantity when all sold`() {
    val buyTx = createTransaction(TransactionType.BUY, BigDecimal("10"), BigDecimal("100"), LocalDate.of(2024, 1, 1))
    val sellTx = createTransaction(TransactionType.SELL, BigDecimal("10"), BigDecimal("120"), LocalDate.of(2024, 1, 10))

    val transactionsSlot = slot<List<PortfolioTransaction>>()

    every { instrumentRepository.findById(1L) } returns Optional.of(testInstrument)
    every { portfolioTransactionRepository.findAllByInstrumentId(1L) } returns listOf(buyTx, sellTx)
    every { portfolioTransactionRepository.saveAll(capture(transactionsSlot)) } answers { transactionsSlot.captured }

    transactionProfitService.recalculateProfitsForInstrument(1L)

    val savedBuyTx = transactionsSlot.captured.find { it.transactionType == TransactionType.BUY }!!
    expect(savedBuyTx.remainingQuantity).toEqualNumerically(BigDecimal.ZERO)
    expect(savedBuyTx.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
  }

  private fun createTransaction(
    type: TransactionType,
    quantity: BigDecimal,
    price: BigDecimal,
    date: LocalDate,
    platform: Platform = Platform.LHV,
  ): PortfolioTransaction =
    PortfolioTransaction(
      instrument = testInstrument,
      transactionType = type,
      quantity = quantity,
      price = price,
      transactionDate = date,
      platform = platform,
      commission = BigDecimal("5"),
    )
}
