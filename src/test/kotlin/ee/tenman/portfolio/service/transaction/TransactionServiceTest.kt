package ee.tenman.portfolio.service.transaction

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.createInstrument
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Optional

class TransactionServiceTest : TransactionServiceTestBase() {
  @Test
  fun `should getTransactionById returns transaction when found`() {
    val transaction = createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"))
    transaction.id = 1L

    every { portfolioTransactionRepository.findById(1L) } returns Optional.of(transaction)

    val result = transactionService.getTransactionById(1L)

    expect(result).toEqual(transaction)
  }

  @Test
  fun `should calculateTransactionProfits for single buy transaction sets zero realized profit`() {
    val buyTx = createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50"))
    testInstrument.currentPrice = BigDecimal("60")

    transactionService.calculateTransactionProfits(listOf(buyTx))

    expect(buyTx.realizedProfit).notToEqualNull().toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("100"))
    expect(buyTx.unrealizedProfit).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateTransactionProfits for buy then sell calculates realized profit correctly`() {
    val buyTx = createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50"), date = testDate.minusDays(10))
    val sellTx = createSellCashFlow(quantity = BigDecimal("40"), price = BigDecimal("70"), date = testDate)
    testInstrument.currentPrice = BigDecimal("65")

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    expect(sellTx.realizedProfit).notToEqualNull().toBeGreaterThan(BigDecimal.ZERO)
    expect(sellTx.averageCost).notToEqualNull()
    expect(sellTx.remainingQuantity).toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("60"))
  }

  @Test
  fun `should calculateTransactionProfits calculates average cost correctly after buy`() {
    val buyTx = createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50"), commission = BigDecimal("10"))
    testInstrument.currentPrice = BigDecimal("60")

    transactionService.calculateTransactionProfits(listOf(buyTx))

    expect(buyTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("50"))
  }

  @Test
  fun `should calculateTransactionProfits with multiple buys calculates blended average cost`() {
    val buy1 = createBuyCashFlow(quantity = BigDecimal("50"), price = BigDecimal("40"), date = testDate.minusDays(20))
    val buy2 = createBuyCashFlow(quantity = BigDecimal("50"), price = BigDecimal("60"), date = testDate.minusDays(10))
    testInstrument.currentPrice = BigDecimal("55")

    transactionService.calculateTransactionProfits(listOf(buy1, buy2))

    expect(buy1.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("40"))
    expect(buy2.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("60"))
  }

  @Test
  fun `should calculateTransactionProfits subtracts commission from sell profit`() {
    val buyTx = createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50"))
    val sellTx = createSellCashFlow(quantity = BigDecimal("100"), price = BigDecimal("60"), commission = BigDecimal("20"))

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    val grossProfit = BigDecimal("100").multiply(BigDecimal("60").subtract(BigDecimal("50.05")))
    val expectedProfit = grossProfit.subtract(BigDecimal("20"))
    expect(sellTx.realizedProfit).notToEqualNull().toEqualNumerically(expectedProfit)
  }

  @Test
  fun `should calculateTransactionProfits handles partial sell correctly`() {
    val buyTx = createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50"))
    val sellTx = createSellCashFlow(quantity = BigDecimal("30"), price = BigDecimal("70"))
    testInstrument.currentPrice = BigDecimal("65")

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    expect(sellTx.realizedProfit).notToEqualNull().toBeGreaterThan(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("70"))
    expect(buyTx.unrealizedProfit).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateTransactionProfits distributes unrealized profit proportionally`() {
    val buy1 = createBuyCashFlow(quantity = BigDecimal("60"), price = BigDecimal("50"), date = testDate.minusDays(20))
    val buy2 = createBuyCashFlow(quantity = BigDecimal("40"), price = BigDecimal("50"), date = testDate.minusDays(10))
    testInstrument.currentPrice = BigDecimal("70")

    transactionService.calculateTransactionProfits(listOf(buy1, buy2))

    val totalUnrealizedProfit = buy1.unrealizedProfit.add(buy2.unrealizedProfit)
    val expectedTotalProfit = BigDecimal("100").multiply(BigDecimal("70").subtract(BigDecimal("50")))

    expect(totalUnrealizedProfit).toEqualNumerically(expectedTotalProfit)
    expect(buy1.unrealizedProfit).toBeGreaterThan(buy2.unrealizedProfit)
  }

  @Test
  fun `should calculateTransactionProfits handles complete sell-off`() {
    val buyTx = createBuyCashFlow(quantity = BigDecimal("50"), price = BigDecimal("100"))
    val sellTx = createSellCashFlow(quantity = BigDecimal("50"), price = BigDecimal("120"))

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    expect(sellTx.realizedProfit).notToEqualNull().toBeGreaterThan(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal.ZERO)
    expect(buyTx.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateTransactionProfits with zero current price sets zero unrealized profit`() {
    val buyTx = createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50"))
    testInstrument.currentPrice = BigDecimal.ZERO

    transactionService.calculateTransactionProfits(listOf(buyTx))

    expect(buyTx.unrealizedProfit).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateTransactionProfits groups by platform and instrument`() {
    val instrument2 = createInstrument(symbol = "GOOGL", name = "Alphabet Inc.", currentPrice = BigDecimal("2800"), id = 2L)

    val tx1 = createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LHV)
    val tx2 = createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"), platform = Platform.LIGHTYEAR)
    val tx3 =
      createBuyCashFlow(quantity = BigDecimal("5"), price = BigDecimal("2500"), platform = Platform.LHV, instrument = instrument2)

    transactionService.calculateTransactionProfits(listOf(tx1, tx2, tx3))

    expect(tx1.unrealizedProfit).toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
    expect(tx2.unrealizedProfit).toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
    expect(tx3.unrealizedProfit).toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateTransactionProfits handles transaction ordering correctly`() {
    val laterTx = createBuyCashFlow(quantity = BigDecimal("50"), price = BigDecimal("60"), date = testDate)
    val earlierTx = createBuyCashFlow(quantity = BigDecimal("50"), price = BigDecimal("40"), date = testDate.minusDays(10))
    testInstrument.currentPrice = BigDecimal("55")

    transactionService.calculateTransactionProfits(listOf(laterTx, earlierTx))

    expect(earlierTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("40"))
    expect(laterTx.averageCost).notToEqualNull().toEqualNumerically(BigDecimal("60"))
  }

  @Test
  fun `should calculateTransactionProfits with multiple sells reduces cost basis correctly`() {
    val buyTx = createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50"), date = testDate.minusDays(30))
    val sell1 = createSellCashFlow(quantity = BigDecimal("30"), price = BigDecimal("60"), date = testDate.minusDays(15))
    val sell2 = createSellCashFlow(quantity = BigDecimal("20"), price = BigDecimal("65"), date = testDate)
    testInstrument.currentPrice = BigDecimal("70")

    transactionService.calculateTransactionProfits(listOf(buyTx, sell1, sell2))

    expect(sell1.realizedProfit).notToEqualNull().toBeGreaterThan(BigDecimal.ZERO)
    expect(sell2.realizedProfit).notToEqualNull().toBeGreaterThan(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("50"))
  }

  @Test
  fun `should calculateTransactionProfits handles loss scenario correctly`() {
    val buyTx = createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("100"))
    val sellTx = createSellCashFlow(quantity = BigDecimal("50"), price = BigDecimal("80"))

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    expect(sellTx.realizedProfit).notToEqualNull().toBeLessThan(BigDecimal.ZERO)
  }

  @Test
  fun `should getAllTransactions calls calculateTransactionProfits`() {
    val transactions = listOf(createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100")))

    every { transactionCacheService.getAllTransactions() } returns transactions

    val result = transactionService.getAllTransactions()

    expect(result).toHaveSize(1)
    verify { transactionCacheService.getAllTransactions() }
  }

  @Test
  fun `should saveTransaction saves and recalculates related profits`() {
    val newTransaction = createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"))
    val savedTransaction = createBuyCashFlow(quantity = BigDecimal("10"), price = BigDecimal("100"))
    savedTransaction.id = 1L

    val relatedTransactions =
      listOf(
        savedTransaction,
        createBuyCashFlow(quantity = BigDecimal("5"), price = BigDecimal("110"), date = testDate.minusDays(5)),
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
    val buyTx = createBuyCashFlow(quantity = BigDecimal("100"), price = BigDecimal("50"), date = testDate.minusDays(30))
    val sellTx = createSellCashFlow(quantity = BigDecimal("40"), price = BigDecimal("55"), date = testDate.minusDays(15))
    testInstrument.currentPrice = null

    transactionService.calculateTransactionProfits(listOf(buyTx, sellTx))

    expect(sellTx.realizedProfit).notToEqualNull().toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
    expect(buyTx.remainingQuantity).toEqualNumerically(BigDecimal("60"))
  }

  @Test
  fun `should report full coverage when the selection contains every platform holding transactions`() {
    every { portfolioTransactionRepository.findDistinctPlatforms() } returns listOf(Platform.LHV, Platform.SWEDBANK)

    expect(transactionService.coversEveryPlatform(listOf(Platform.SWEDBANK, Platform.LHV, Platform.BINANCE))).toEqual(true)
  }

  @Test
  fun `should report partial coverage when the selection omits a platform holding transactions`() {
    every { portfolioTransactionRepository.findDistinctPlatforms() } returns listOf(Platform.LHV, Platform.SWEDBANK)

    expect(transactionService.coversEveryPlatform(listOf(Platform.LHV))).toEqual(false)
  }
}
