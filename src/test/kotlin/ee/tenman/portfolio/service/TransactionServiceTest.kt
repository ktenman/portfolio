package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.*

class TransactionServiceTest {
  private lateinit var portfolioTransactionRepository: PortfolioTransactionRepository

  private lateinit var transactionService: TransactionService

  private lateinit var testInstrument: Instrument
  private val testDate = LocalDate.of(2024, 1, 15)

  @BeforeEach
  fun setUp() {
    portfolioTransactionRepository = mockk()
    transactionService = TransactionService(portfolioTransactionRepository)

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
  fun `should getTransactionById returns transaction when found`() {
    val transaction = createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"))
    transaction.id = 1L

    every { portfolioTransactionRepository.findById(1L) } returns Optional.of(transaction)

    val result = transactionService.getTransactionById(1L)

    expect(result).toEqual(transaction)
  }

  @Test
  fun `should calculateTransactionProfits for single buy transaction sets zero realized profit`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"))
    testInstrument.currentPrice = BigDecimal("60")

    transactionService.calculateTransactionProfits(listOf(buyTx))

    expect(buyTx.realizedProfit).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("100"))
    expect(buyTx.unrealizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
  }

  @Test
  fun `should calculateTransactionProfits for buy then sell calculates realized profit correctly`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"), date = testDate.minusDays(10))
    val sellTx = createSellTransaction(quantity = BigDecimal("40"), price = BigDecimal("70"), date = testDate)
    testInstrument.currentPrice = BigDecimal("65")

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    expect(sellTx.realizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
    expect(sellTx.averageCost).notToEqualNull()
    expect(sellTx.remainingQuantity).toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("60"))
  }

  @Test
  fun `should calculateTransactionProfits calculates average cost correctly after buy`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"), commission = BigDecimal("10"))
    testInstrument.currentPrice = BigDecimal("60")

    transactionService.calculateTransactionProfits(listOf(buyTx))

    expect(buyTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("50"))
  }

  @Test
  fun `should calculateTransactionProfits with multiple buys calculates blended average cost`() {
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

    expect(buy1.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("40"))
    expect(buy2.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("60"))
  }

  @Test
  fun `should calculateTransactionProfits subtracts commission from sell profit`() {
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
    expect(sellTx.realizedProfit).notToEqualNull().toEqualNumerically(expectedProfit)
  }

  @Test
  fun `should calculateTransactionProfits handles partial sell correctly`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"))
    val sellTx = createSellTransaction(quantity = BigDecimal("30"), price = BigDecimal("70"))
    testInstrument.currentPrice = BigDecimal("65")

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    expect(sellTx.realizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("70"))
    expect(buyTx.unrealizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
  }

  @Test
  fun `should calculateTransactionProfits distributes unrealized profit proportionally`() {
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
    val expectedTotalProfit = BigDecimal("100").multiply(BigDecimal("70").subtract(BigDecimal("50")))

    expect(totalUnrealizedProfit).toEqualNumerically(expectedTotalProfit)
    expect(buy1.unrealizedProfit).toBeGreaterThan(buy2.unrealizedProfit)
  }

  @Test
  fun `should calculateTransactionProfits handles complete sell-off`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("50"), price = BigDecimal("100"))
    val sellTx = createSellTransaction(quantity = BigDecimal("50"), price = BigDecimal("120"))

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    expect(sellTx.realizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateTransactionProfits with zero current price sets zero unrealized profit`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"))
    testInstrument.currentPrice = BigDecimal.ZERO

    transactionService.calculateTransactionProfits(listOf(buyTx))

    expect(buyTx.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateTransactionProfits groups by platform and instrument`() {
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

    expect(tx1.unrealizedProfit).toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
    expect(tx2.unrealizedProfit).toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
    expect(tx3.unrealizedProfit).toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateTransactionProfits handles transaction ordering correctly`() {
    val laterTx = createBuyTransaction(quantity = BigDecimal("50"), price = BigDecimal("60"), date = testDate)
    val earlierTx =
      createBuyTransaction(
        quantity = BigDecimal("50"),
        price = BigDecimal("40"),
        date = testDate.minusDays(10),
      )
    testInstrument.currentPrice = BigDecimal("55")

    transactionService.calculateTransactionProfits(listOf(laterTx, earlierTx))

    expect(earlierTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("40"))
    expect(laterTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("60"))
  }

  @Test
  fun `should calculateTransactionProfits with multiple sells reduces cost basis correctly`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"), date = testDate.minusDays(30))
    val sell1 = createSellTransaction(quantity = BigDecimal("30"), price = BigDecimal("60"), date = testDate.minusDays(15))
    val sell2 = createSellTransaction(quantity = BigDecimal("20"), price = BigDecimal("65"), date = testDate)
    testInstrument.currentPrice = BigDecimal("70")

    transactionService.calculateTransactionProfits(listOf(buyTx, sell1, sell2))

    expect(sell1.realizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
    expect(sell2.realizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("50"))
  }

  @Test
  fun `should calculateTransactionProfits handles loss scenario correctly`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("100"))
    val sellTx = createSellTransaction(quantity = BigDecimal("50"), price = BigDecimal("80"))

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    expect(sellTx.realizedProfit!!.compareTo(BigDecimal.ZERO)).toBeLessThan(0)
  }

  @Test
  fun `should getAllTransactions calls calculateTransactionProfits`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")),
      )

    every { portfolioTransactionRepository.findAllWithInstruments() } returns transactions

    val result = transactionService.getAllTransactions()

    expect(result).toHaveSize(1)
    verify { portfolioTransactionRepository.findAllWithInstruments() }
  }

  @Test
  fun `should saveTransaction saves and recalculates related profits`() {
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

    every { portfolioTransactionRepository.save(any()) } returns savedTransaction
    every {
      portfolioTransactionRepository.findAllByInstrumentIdAndPlatformOrderByTransactionDate(
        1L,
        any(),
      )
    } returns relatedTransactions
    every { portfolioTransactionRepository.saveAll(any<List<PortfolioTransaction>>()) } returns relatedTransactions

    val result = transactionService.saveTransaction(newTransaction)

    expect(result.id).toEqual(1L)
    verify { portfolioTransactionRepository.save(newTransaction) }
    verify { portfolioTransactionRepository.saveAll(match<List<PortfolioTransaction>> { it.size == 2 }) }
  }

  @Test
  fun `should deleteTransaction removes transaction from repository`() {
    every { portfolioTransactionRepository.deleteById(1L) } returns Unit

    transactionService.deleteTransaction(1L)

    verify { portfolioTransactionRepository.deleteById(1L) }
  }

  @Test
  fun `should calculateTransactionProfits with sell before current price updates works correctly`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"), date = testDate.minusDays(30))
    val sellTx = createSellTransaction(quantity = BigDecimal("40"), price = BigDecimal("55"), date = testDate.minusDays(15))
    testInstrument.currentPrice = null

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    expect(sellTx.realizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThanOrEqualTo(0)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("60"))
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

  @Test
  fun `should processBuyTransaction accumulates cost correctly with commission`() {
    val buyTx =
      createBuyTransaction(
        quantity = BigDecimal("100"),
        price = BigDecimal("50"),
        commission = BigDecimal("25"),
      )

    transactionService.calculateTransactionProfits(listOf(buyTx))

    expect(buyTx.realizedProfit).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("100"))
    expect(buyTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("50"))
  }

  @Test
  fun `should processBuyTransaction sets realized profit to zero`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("50"), price = BigDecimal("100"))

    transactionService.calculateTransactionProfits(listOf(buyTx))

    expect(buyTx.realizedProfit).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should processSellTransaction calculates realized profit with average cost`() {
    val buyTx =
      createBuyTransaction(
        quantity = BigDecimal("100"),
        price = BigDecimal("40"),
        date = testDate.minusDays(10),
      )
    val sellTx =
      createSellTransaction(
        quantity = BigDecimal("50"),
        price = BigDecimal("60"),
        commission = BigDecimal("10"),
      )

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    val avgCost =
      BigDecimal("40")
        .multiply(BigDecimal("100"))
        .add(BigDecimal("5"))
      .divide(BigDecimal("100"), 10, RoundingMode.HALF_UP)
    val grossProfit = BigDecimal("50").multiply(BigDecimal("60").subtract(avgCost))
    val expectedProfit = grossProfit.subtract(BigDecimal("10"))

    expect(sellTx.realizedProfit).notToEqualNull().toEqualNumerically(expectedProfit)
    expect(sellTx.averageCost).notToEqualNull().toEqualNumerically(avgCost)
    expect(sellTx.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
    expect(sellTx.remainingQuantity).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should processSellTransaction reduces total cost proportionally`() {
    val buyTx =
      createBuyTransaction(
        quantity = BigDecimal("100"),
        price = BigDecimal("50"),
        date = testDate.minusDays(10),
      )
    val sellTx = createSellTransaction(quantity = BigDecimal("40"), price = BigDecimal("70"))
    testInstrument.currentPrice = BigDecimal("65")

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("60"))
    expect(sellTx.realizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
  }

  @Test
  fun `should calculateAverageCost returns zero when quantity is zero`() {
    val sellTx =
      createSellTransaction(
        quantity = BigDecimal("50"),
        price = BigDecimal("100"),
      )

    transactionService.calculateTransactionProfits(listOf(sellTx))

    expect(sellTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateAverageCost divides total cost by quantity correctly`() {
    val buy1 =
      createBuyTransaction(
        quantity = BigDecimal("30"),
        price = BigDecimal("100"),
        commission = BigDecimal("15"),
        date = testDate.minusDays(20),
      )
    val buy2 =
      createBuyTransaction(
        quantity = BigDecimal("70"),
        price = BigDecimal("120"),
        commission = BigDecimal("35"),
        date = testDate.minusDays(10),
      )

    transactionService.calculateTransactionProfits(listOf(buy1, buy2))

    expect(buy1.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("100"))
    expect(buy2.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("120"))
  }

  @Test
  fun `should distributeUnrealizedProfits sets zero metrics when current quantity is zero`() {
    val buyTx =
      createBuyTransaction(
        quantity = BigDecimal("50"),
        price = BigDecimal("100"),
        date = testDate.minusDays(10),
      )
    val sellTx = createSellTransaction(quantity = BigDecimal("50"), price = BigDecimal("110"))

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.averageCost).notToEqualNull().toEqualNumerically(buyTx.price)
  }

  @Test
  fun `should distributeUnrealizedProfits calculates proportional quantities for multiple buys`() {
    val buy1 =
      createBuyTransaction(
        quantity = BigDecimal("30"),
        price = BigDecimal("50"),
        date = testDate.minusDays(20),
      )
    val buy2 =
      createBuyTransaction(
        quantity = BigDecimal("70"),
        price = BigDecimal("50"),
        date = testDate.minusDays(10),
      )
    testInstrument.currentPrice = BigDecimal("60")

    transactionService.calculateTransactionProfits(listOf(buy1, buy2))

    val totalRemaining = buy1.remainingQuantity.add(buy2.remainingQuantity)
    expect(totalRemaining).toEqualNumerically(BigDecimal("100"))

    val ratio1 = buy1.remainingQuantity.divide(totalRemaining, 10, RoundingMode.HALF_UP)
    val ratio2 = buy2.remainingQuantity.divide(totalRemaining, 10, RoundingMode.HALF_UP)

    expect(ratio1).toEqualNumerically(BigDecimal("0.3"))
    expect(ratio2).toEqualNumerically(BigDecimal("0.7"))
  }

  @Test
  fun `should distributeUnrealizedProfits distributes profit proportionally to remaining quantity`() {
    val buy1 =
      createBuyTransaction(
        quantity = BigDecimal("40"),
        price = BigDecimal("50"),
        date = testDate.minusDays(20),
      )
    val buy2 =
      createBuyTransaction(
        quantity = BigDecimal("60"),
        price = BigDecimal("50"),
        date = testDate.minusDays(10),
      )
    testInstrument.currentPrice = BigDecimal("70")

    transactionService.calculateTransactionProfits(listOf(buy1, buy2))

    val totalUnrealizedProfit = buy1.unrealizedProfit.add(buy2.unrealizedProfit)
    val expectedTotalProfit = BigDecimal("100").multiply(BigDecimal("70").subtract(BigDecimal("50")))

    expect(totalUnrealizedProfit).toEqualNumerically(expectedTotalProfit)

    val profitRatio1 = buy1.unrealizedProfit.divide(totalUnrealizedProfit, 10, RoundingMode.HALF_UP)
    val profitRatio2 = buy2.unrealizedProfit.divide(totalUnrealizedProfit, 10, RoundingMode.HALF_UP)

    expect(profitRatio1).toEqualNumerically(BigDecimal("0.4"))
    expect(profitRatio2).toEqualNumerically(BigDecimal("0.6"))
  }

  @Test
  fun `should calculateProfitsForPlatform handles buy only scenario`() {
    val buy1 =
      createBuyTransaction(
        quantity = BigDecimal("25"),
        price = BigDecimal("80"),
        date = testDate.minusDays(15),
      )
    val buy2 =
      createBuyTransaction(
        quantity = BigDecimal("75"),
        price = BigDecimal("100"),
        date = testDate.minusDays(5),
      )
    testInstrument.currentPrice = BigDecimal("110")

    transactionService.calculateTransactionProfits(listOf(buy1, buy2))

    expect(buy1.realizedProfit).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
    expect(buy2.realizedProfit).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
    expect(buy1.unrealizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
    expect(buy2.unrealizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
    expect(buy1.remainingQuantity.add(buy2.remainingQuantity)).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should calculateProfitsForPlatform handles sell only scenario with zero current quantity`() {
    val sellTx = createSellTransaction(quantity = BigDecimal("50"), price = BigDecimal("100"))

    transactionService.calculateTransactionProfits(listOf(sellTx))

    expect(sellTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
    val expectedProfit = BigDecimal("50").multiply(BigDecimal("100")).subtract(BigDecimal("5"))
    expect(sellTx.realizedProfit).notToEqualNull().toEqualNumerically(expectedProfit)
    expect(sellTx.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
    expect(sellTx.remainingQuantity).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateProfitsForPlatform handles mixed buy sell buy sequence`() {
    val buy1 =
      createBuyTransaction(
        quantity = BigDecimal("100"),
        price = BigDecimal("50"),
        date = testDate.minusDays(30),
      )
    val sell1 =
      createSellTransaction(
        quantity = BigDecimal("60"),
        price = BigDecimal("70"),
        date = testDate.minusDays(15),
      )
    val buy2 =
      createBuyTransaction(
        quantity = BigDecimal("50"),
        price = BigDecimal("65"),
        date = testDate.minusDays(5),
      )
    testInstrument.currentPrice = BigDecimal("80")

    transactionService.calculateTransactionProfits(listOf(buy1, sell1, buy2))

    expect(sell1.realizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
    expect(buy1.remainingQuantity.add(buy2.remainingQuantity)).toEqualNumerically(BigDecimal("90"))
    expect(buy1.unrealizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
    expect(buy2.unrealizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
  }

  @Test
  fun `should calculateProfitsForPlatform handles edge case with single transaction`() {
    val singleBuy = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("75"))
    testInstrument.currentPrice = BigDecimal("90")

    transactionService.calculateTransactionProfits(listOf(singleBuy))

    expect(singleBuy.realizedProfit).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
    expect(singleBuy.remainingQuantity).toEqualNumerically(BigDecimal("100"))
    expect(singleBuy.unrealizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
  }

  @Test
  fun `should calculateProfitsForPlatform handles complete selloff then new buy`() {
    val buy1 =
      createBuyTransaction(
        quantity = BigDecimal("50"),
        price = BigDecimal("100"),
        date = testDate.minusDays(30),
      )
    val sell1 =
      createSellTransaction(
        quantity = BigDecimal("50"),
        price = BigDecimal("120"),
        date = testDate.minusDays(15),
      )
    val buy2 =
      createBuyTransaction(
        quantity = BigDecimal("30"),
        price = BigDecimal("110"),
        date = testDate.minusDays(5),
      )
    testInstrument.currentPrice = BigDecimal("125")

    transactionService.calculateTransactionProfits(listOf(buy1, sell1, buy2))

    expect(sell1.realizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
    expect(buy2.unrealizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
    expect(buy1.unrealizedProfit.add(buy2.unrealizedProfit).compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
  }

  @Test
  fun `should calculateProfitsForPlatform handles zero current price scenario`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"))
    testInstrument.currentPrice = BigDecimal.ZERO

    transactionService.calculateTransactionProfits(listOf(buyTx))

    expect(buyTx.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should calculateProfitsForPlatform handles null current price`() {
    val buyTx = createBuyTransaction(quantity = BigDecimal("100"), price = BigDecimal("50"))
    testInstrument.currentPrice = null

    transactionService.calculateTransactionProfits(listOf(buyTx))

    expect(buyTx.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should calculateProfitsForPlatform with oversell scenario`() {
    val buyTx =
      createBuyTransaction(
        quantity = BigDecimal("50"),
        price = BigDecimal("100"),
        date = testDate.minusDays(10),
      )
    val sellTx = createSellTransaction(quantity = BigDecimal("80"), price = BigDecimal("110"))

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    expect(buyTx.remainingQuantity!!.compareTo(BigDecimal.ZERO)).toBeLessThanOrEqualTo(0)
    expect(buyTx.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateProfitsForPlatform calculates correct average cost after multiple buys and sells`() {
    val buy1 =
      createBuyTransaction(
        quantity = BigDecimal("100"),
        price = BigDecimal("40"),
        commission = BigDecimal("10"),
        date = testDate.minusDays(40),
      )
    val buy2 =
      createBuyTransaction(
        quantity = BigDecimal("100"),
        price = BigDecimal("60"),
        commission = BigDecimal("10"),
        date = testDate.minusDays(30),
      )
    val sell1 =
      createSellTransaction(
        quantity = BigDecimal("100"),
        price = BigDecimal("70"),
        commission = BigDecimal("15"),
        date = testDate.minusDays(15),
      )
    testInstrument.currentPrice = BigDecimal("80")

    transactionService.calculateTransactionProfits(listOf(buy1, buy2, sell1))

    val totalInitialCost =
      BigDecimal("40")
        .multiply(BigDecimal("100"))
        .add(BigDecimal("10"))
      .add(BigDecimal("60").multiply(BigDecimal("100")))
        .add(BigDecimal("10"))
    val avgCostBeforeSell = totalInitialCost.divide(BigDecimal("200"), 10, RoundingMode.HALF_UP)

    expect(sell1.averageCost).notToEqualNull().toEqualNumerically(avgCostBeforeSell)
    expect(buy1.remainingQuantity.add(buy2.remainingQuantity)).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should calculateProfitsForPlatform with high precision decimal values`() {
    val buyTx =
      createBuyTransaction(
        quantity = BigDecimal("33.333333"),
        price = BigDecimal("99.999999"),
        commission = BigDecimal("3.141592"),
      )
    testInstrument.currentPrice = BigDecimal("123.456789")

    transactionService.calculateTransactionProfits(listOf(buyTx))

    expect(buyTx.realizedProfit).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("33.333333"))
    expect(buyTx.unrealizedProfit!!.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)
  }
}
