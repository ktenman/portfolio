package ee.tenman.portfolio.repository

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.time.LocalDate

@IntegrationTest
@TestPropertySource(properties = ["spring.jpa.show-sql=true"])
class PortfolioTransactionRepositoryIT {
  @Resource
  private lateinit var portfolioTransactionRepository: PortfolioTransactionRepository

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  private lateinit var testInstrument: Instrument
  private val testDate = LocalDate.of(2024, 1, 15)

  @BeforeEach
  fun setup() {
    portfolioTransactionRepository.deleteAll()
    instrumentRepository.deleteAll()

    testInstrument =
      instrumentRepository.save(
        Instrument(
          symbol = "TEST",
          name = "Test Instrument",
          category = "Stock",
          baseCurrency = "USD",
        ),
      )
  }

  @Test
  fun `should find transactions by single platform`() {
    val binanceTransaction1 =
      createAndSaveCashFlow(Platform.BINANCE, BigDecimal("100"), testDate.minusDays(1))
    val binanceTransaction2 =
      createAndSaveCashFlow(Platform.BINANCE, BigDecimal("200"), testDate)
    createAndSaveCashFlow(Platform.TRADING212, BigDecimal("300"), testDate)
    createAndSaveCashFlow(Platform.LIGHTYEAR, BigDecimal("400"), testDate)

    val result = portfolioTransactionRepository.findAllByPlatformsWithInstruments(listOf(Platform.BINANCE))

    expect(result).toHaveSize(2)
    expect(result.map { it.id }).toContainExactly(binanceTransaction2.id, binanceTransaction1.id)
    expect(result.all { it.platform == Platform.BINANCE }).toEqual(true)
    expect(result.all { it.instrument.id == testInstrument.id }).toEqual(true)
  }

  @Test
  fun `should find transactions by multiple platforms`() {
    val binanceTransaction =
      createAndSaveCashFlow(Platform.BINANCE, BigDecimal("100"), testDate)
    val trading212Transaction =
      createAndSaveCashFlow(Platform.TRADING212, BigDecimal("200"), testDate)
    createAndSaveCashFlow(Platform.LIGHTYEAR, BigDecimal("300"), testDate)
    createAndSaveCashFlow(Platform.SWEDBANK, BigDecimal("400"), testDate)

    val result =
      portfolioTransactionRepository.findAllByPlatformsWithInstruments(
        listOf(Platform.BINANCE, Platform.TRADING212),
      )

    expect(result).toHaveSize(2)
    val resultIds = result.map { it.id }
    expect(resultIds.contains(binanceTransaction.id)).toEqual(true)
    expect(resultIds.contains(trading212Transaction.id)).toEqual(true)
    expect(result.any { it.platform == Platform.BINANCE }).toEqual(true)
    expect(result.any { it.platform == Platform.TRADING212 }).toEqual(true)
  }

  @Test
  fun `should return empty list when no transactions match platforms`() {
    createAndSaveCashFlow(Platform.BINANCE, BigDecimal("100"), testDate)
    createAndSaveCashFlow(Platform.TRADING212, BigDecimal("200"), testDate)

    val result =
      portfolioTransactionRepository.findAllByPlatformsWithInstruments(
        listOf(Platform.LIGHTYEAR, Platform.SWEDBANK),
      )

    expect(result).toHaveSize(0)
  }

  @Test
  fun `should return transactions ordered by date and id descending`() {
    createAndSaveCashFlow(Platform.BINANCE, BigDecimal("100"), testDate.minusDays(2))
    val transaction2 =
      createAndSaveCashFlow(Platform.BINANCE, BigDecimal("200"), testDate)
    createAndSaveCashFlow(Platform.BINANCE, BigDecimal("300"), testDate.minusDays(1))
    val transaction4 =
      createAndSaveCashFlow(Platform.BINANCE, BigDecimal("400"), testDate)

    val result = portfolioTransactionRepository.findAllByPlatformsWithInstruments(listOf(Platform.BINANCE))

    expect(result).toHaveSize(4)
    expect(result[0].transactionDate).toEqual(testDate)
    expect(result[1].transactionDate).toEqual(testDate)
    expect(result[2].transactionDate).toEqual(testDate.minusDays(1))
    expect(result[3].transactionDate).toEqual(testDate.minusDays(2))

    val todayTransactions = result.take(2)
    expect(todayTransactions[0].id > todayTransactions[1].id).toEqual(true)
    expect(todayTransactions[0].id).toEqual(transaction4.id)
    expect(todayTransactions[1].id).toEqual(transaction2.id)
  }

  @Test
  fun `should fetch instruments eagerly with transactions`() {
    createAndSaveCashFlow(Platform.BINANCE, BigDecimal("100"), testDate)

    val result = portfolioTransactionRepository.findAllByPlatformsWithInstruments(listOf(Platform.BINANCE))

    expect(result).toHaveSize(1)
    val transaction = result[0]
    expect(transaction.instrument.symbol).toEqual("TEST")
    expect(transaction.instrument.name).toEqual("Test Instrument")
  }

  @Test
  fun `should handle empty platforms list`() {
    createAndSaveCashFlow(Platform.BINANCE, BigDecimal("100"), testDate)

    val result = portfolioTransactionRepository.findAllByPlatformsWithInstruments(emptyList())

    expect(result).toHaveSize(0)
  }

  private fun createAndSaveCashFlow(
    platform: Platform,
    price: BigDecimal,
    date: LocalDate,
  ): PortfolioTransaction =
    portfolioTransactionRepository.save(
      PortfolioTransaction(
        instrument = testInstrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = price,
        transactionDate = date,
        platform = platform,
      ),
    )
}
