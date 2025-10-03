package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class TransactionServiceTest {
  @Mock
  private lateinit var portfolioTransactionRepository: PortfolioTransactionRepository

  @InjectMocks
  private lateinit var transactionService: TransactionService

  private lateinit var testInstrument: Instrument
  private val testDate = LocalDate.of(2024, 1, 15)

  @BeforeEach
  fun setUp() {
    testInstrument =
      Instrument(
        symbol = "AAPL",
        name = "Apple Inc.",
        category = "Stock",
        baseCurrency = "USD",
        currentPrice = BigDecimal("150.00"),
        providerName = ProviderName.ALPHA_VANTAGE,
      ).apply {
        id = 1L
      }
  }

  @Test
  fun `getTransactionById returns transaction when found`() {
    val transaction = createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"))
    transaction.id = 1L

    whenever(portfolioTransactionRepository.findById(1L)).thenReturn(Optional.of(transaction))

    val result = transactionService.getTransactionById(1L)

    assertThat(result).isEqualTo(transaction)
  }

  @Test
  fun `calculateTransactionProfits for single buy transaction sets zero realized profit`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"))
    testInstrument.currentPrice = BigDecimal("60")

    transactionService.calculateTransactionProfits(listOf(buyTx))

    assertThat(buyTx.realizedProfit).isEqualByComparingTo(BigDecimal.ZERO)
    assertThat(buyTx.remainingQuantity).isEqualByComparingTo(BigDecimal("100"))
    assertThat(buyTx.unrealizedProfit).isGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `calculateTransactionProfits for buy then sell calculates realized profit correctly`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"), date = testDate.minusDays(10))
    val sellTx = createSellTransaction(quantity = BigDecimal("40"), price = BigDecimal("70"), date = testDate)
    testInstrument.currentPrice = BigDecimal("65")

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    assertThat(sellTx.realizedProfit).isGreaterThan(BigDecimal.ZERO)
    assertThat(sellTx.averageCost).isNotNull()
    assertThat(sellTx.remainingQuantity).isEqualByComparingTo(BigDecimal.ZERO)
    assertThat(buyTx.remainingQuantity).isEqualByComparingTo(BigDecimal("60"))
  }

  @Test
  fun `calculateTransactionProfits calculates average cost correctly after buy`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"), commission = BigDecimal("10"))
    testInstrument.currentPrice = BigDecimal("60")

    transactionService.calculateTransactionProfits(listOf(buyTx))

    val expectedAvgCost =
      (BigDecimal("50").multiply(BigDecimal("100")).add(BigDecimal("10")))
      .divide(BigDecimal("100"), 10, RoundingMode.HALF_UP)
    assertThat(buyTx.averageCost).isEqualByComparingTo(expectedAvgCost)
  }

  @Test
  fun `calculateTransactionProfits with multiple buys calculates blended average cost`() {
    val buy1 =
      createBuyTransaction(
        quantity = BigDecimal("50"),
        price = BigDecimal("40"),
        date = testDate.minusDays(20),
      )
    val buy2 =
      createBuyTransaction(
        quantity = BigDecimal("50"),
        price = BigDecimal("60"),
        date = testDate.minusDays(10),
      )
    testInstrument.currentPrice = BigDecimal("55")

    transactionService.calculateTransactionProfits(listOf(buy1, buy2))

    val expectedAvgCost = BigDecimal("50.10")
    assertThat(buy1.averageCost).isEqualByComparingTo(expectedAvgCost)
    assertThat(buy2.averageCost).isEqualByComparingTo(expectedAvgCost)
  }

  @Test
  fun `calculateTransactionProfits subtracts commission from sell profit`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"))
    val sellTx =
      createSellTransaction(
        quantity = BigDecimal("100"),
        price = BigDecimal("60"),
        commission = BigDecimal("20"),
      )

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    val grossProfit = BigDecimal("100").multiply(BigDecimal("60").subtract(BigDecimal("50.05")))
    val expectedProfit = grossProfit.subtract(BigDecimal("20"))
    assertThat(sellTx.realizedProfit).isEqualByComparingTo(expectedProfit)
  }

  @Test
  fun `calculateTransactionProfits handles partial sell correctly`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"))
    val sellTx = createSellTransaction(quantity = BigDecimal("30"), price = BigDecimal("70"))
    testInstrument.currentPrice = BigDecimal("65")

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    assertThat(sellTx.realizedProfit).isGreaterThan(BigDecimal.ZERO)
    assertThat(buyTx.remainingQuantity).isEqualByComparingTo(BigDecimal("70"))
    assertThat(buyTx.unrealizedProfit).isGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `calculateTransactionProfits distributes unrealized profit proportionally`() {
    val buy1 =
      createBuyTransaction(
        quantity = BigDecimal("60"),
        price = BigDecimal("50"),
        date = testDate.minusDays(20),
      )
    val buy2 =
      createBuyTransaction(
        quantity = BigDecimal("40"),
        price = BigDecimal("50"),
        date = testDate.minusDays(10),
      )
    testInstrument.currentPrice = BigDecimal("70")

    transactionService.calculateTransactionProfits(listOf(buy1, buy2))

    val totalUnrealizedProfit = buy1.unrealizedProfit.add(buy2.unrealizedProfit)
    val expectedTotalProfit = BigDecimal("100").multiply(BigDecimal("70").subtract(BigDecimal("50.05")))

    assertThat(totalUnrealizedProfit).isCloseTo(expectedTotalProfit, within(BigDecimal("10")))
    assertThat(buy1.unrealizedProfit).isGreaterThan(buy2.unrealizedProfit)
  }

  @Test
  fun `calculateTransactionProfits handles complete sell-off`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("50"), price = BigDecimal("100"))
    val sellTx = createSellTransaction(quantity = BigDecimal("50"), price = BigDecimal("120"))

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    assertThat(sellTx.realizedProfit).isGreaterThan(BigDecimal.ZERO)
    assertThat(buyTx.remainingQuantity).isEqualByComparingTo(BigDecimal.ZERO)
    assertThat(buyTx.unrealizedProfit).isEqualByComparingTo(BigDecimal.ZERO)
  }

  @Test
  fun `calculateTransactionProfits with zero current price sets zero unrealized profit`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"))
    testInstrument.currentPrice = BigDecimal.ZERO

    transactionService.calculateTransactionProfits(listOf(buyTx))

    assertThat(buyTx.unrealizedProfit).isEqualByComparingTo(BigDecimal.ZERO)
  }

  @Test
  fun `calculateTransactionProfits groups by platform and instrument`() {
    val instrument2 =
      Instrument(
        symbol = "GOOGL",
        name = "Alphabet Inc.",
        category = "Stock",
        baseCurrency = "USD",
        currentPrice = BigDecimal("2800"),
      ).apply { id = 2L }

    val tx1 =
      createBuyTransaction(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        platform = Platform.LHV,
      )
    val tx2 =
      createBuyTransaction(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        platform = Platform.LIGHTYEAR,
      )
    val tx3 =
      createBuyTransaction(
        quantity = BigDecimal("5"),
        price = BigDecimal("2500"),
        platform = Platform.LHV,
        instrument = instrument2,
      )

    transactionService.calculateTransactionProfits(listOf(tx1, tx2, tx3))

    assertThat(tx1.unrealizedProfit).isNotNull()
    assertThat(tx2.unrealizedProfit).isNotNull()
    assertThat(tx3.unrealizedProfit).isNotNull()
  }

  @Test
  fun `calculateTransactionProfits handles transaction ordering correctly`() {
    val laterTx = createBuyTransaction(quantity = BigDecimal("50"), price = BigDecimal("60"), date = testDate)
    val earlierTx =
      createBuyTransaction(
        quantity = BigDecimal("50"),
        price = BigDecimal("40"),
        date = testDate.minusDays(10),
      )
    testInstrument.currentPrice = BigDecimal("55")

    transactionService.calculateTransactionProfits(listOf(laterTx, earlierTx))

    val expectedAvgCost = BigDecimal("50.10")
    assertThat(earlierTx.averageCost).isEqualByComparingTo(expectedAvgCost)
    assertThat(laterTx.averageCost).isEqualByComparingTo(expectedAvgCost)
  }

  @Test
  fun `calculateTransactionProfits with multiple sells reduces cost basis correctly`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"), date = testDate.minusDays(30))
    val sell1 = createSellTransaction(quantity = BigDecimal("30"), price = BigDecimal("60"), date = testDate.minusDays(15))
    val sell2 = createSellTransaction(quantity = BigDecimal("20"), price = BigDecimal("65"), date = testDate)
    testInstrument.currentPrice = BigDecimal("70")

    transactionService.calculateTransactionProfits(listOf(buyTx, sell1, sell2))

    assertThat(sell1.realizedProfit).isGreaterThan(BigDecimal.ZERO)
    assertThat(sell2.realizedProfit).isGreaterThan(BigDecimal.ZERO)
    assertThat(buyTx.remainingQuantity).isEqualByComparingTo(BigDecimal("50"))
  }

  @Test
  fun `calculateTransactionProfits handles loss scenario correctly`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("100"))
    val sellTx = createSellTransaction(quantity = BigDecimal("50"), price = BigDecimal("80"))

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    assertThat(sellTx.realizedProfit).isLessThan(BigDecimal.ZERO)
  }

  @Test
  fun `getAllTransactions calls calculateTransactionProfits`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")),
      )

    whenever(portfolioTransactionRepository.findAllWithInstruments()).thenReturn(transactions)

    val result = transactionService.getAllTransactions()

    assertThat(result).hasSize(1)
    verify(portfolioTransactionRepository).findAllWithInstruments()
  }

  @Test
  fun `saveTransaction saves and recalculates related profits`() {
    val newTransaction = createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"))
    val savedTransaction = createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"))
    savedTransaction.id = 1L

    val relatedTransactions =
      listOf(
        savedTransaction,
        createBuyTransaction(
          quantity = BigDecimal("5"),
          price = BigDecimal("110"),
          date = testDate.minusDays(5),
        ),
      )

    whenever(portfolioTransactionRepository.save(any())).thenReturn(savedTransaction)
    whenever(
      portfolioTransactionRepository.findAllByInstrumentIdAndPlatformOrderByTransactionDate(
        eq(1L),
        any(),
      ),
    ).thenReturn(relatedTransactions)
    whenever(portfolioTransactionRepository.saveAll(any<List<PortfolioTransaction>>())).thenReturn(relatedTransactions)

    val result = transactionService.saveTransaction(newTransaction)

    assertThat(result).isNotNull()
    assertThat(result.id).isEqualTo(1L)
    verify(portfolioTransactionRepository).save(newTransaction)
    verify(portfolioTransactionRepository).saveAll(argThat<List<PortfolioTransaction>> { size == 2 })
  }

  @Test
  fun `deleteTransaction removes transaction from repository`() {
    transactionService.deleteTransaction(1L)

    verify(portfolioTransactionRepository).deleteById(1L)
  }

  @Test
  fun `calculateTransactionProfits with sell before current price updates works correctly`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"), date = testDate.minusDays(30))
    val sellTx = createSellTransaction(quantity = BigDecimal("40"), price = BigDecimal("55"), date = testDate.minusDays(15))
    testInstrument.currentPrice = null

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    assertThat(sellTx.realizedProfit).isGreaterThanOrEqualTo(BigDecimal.ZERO)
    assertThat(buyTx.remainingQuantity).isEqualByComparingTo(BigDecimal("60"))
  }

  private fun createBuyTransaction(
    quantity: BigDecimal,
    price: BigDecimal,
    date: LocalDate = testDate,
    commission: BigDecimal = BigDecimal("5"),
    platform: Platform = Platform.LHV,
    instrument: Instrument = testInstrument,
  ): PortfolioTransaction =
    PortfolioTransaction(
      instrument = instrument,
      transactionType = TransactionType.BUY,
      quantity = quantity,
      price = price,
      transactionDate = date,
      platform = platform,
      commission = commission,
    )

  private fun createSellTransaction(
    quantity: BigDecimal,
    price: BigDecimal,
    date: LocalDate = testDate,
    commission: BigDecimal = BigDecimal("5"),
    platform: Platform = Platform.LHV,
  ): PortfolioTransaction =
    PortfolioTransaction(
      instrument = testInstrument,
      transactionType = TransactionType.SELL,
      quantity = quantity,
      price = price,
      transactionDate = date,
      platform = platform,
      commission = commission,
    )
}
