package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.service.xirr.Transaction
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigDecimal
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.localDate
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class XirrCalculationPropertyTest {
  private lateinit var xirrCalculationService: XirrCalculationService
  private lateinit var testInstrument: Instrument

  @BeforeEach
  fun setUp() {
    xirrCalculationService = XirrCalculationService()
    testInstrument =
      Instrument(
      symbol = "TEST",
      name = "Test Instrument",
      category = "Stock",
      baseCurrency = "EUR",
      currentPrice = BigDecimal("100.00"),
      providerName = ProviderName.FT,
    ).apply { id = 1L }
  }

  @Test
  fun `XIRR result is always bounded between -10 and 10`() =
    runBlocking {
    val transactionArb = createXirrTransactionArb()
    checkAll(100, Arb.list(transactionArb, 2..10)) { transactions ->
      val calculationDate = LocalDate.now()
      val xirr = xirrCalculationService.calculateAdjustedXirr(transactions, calculationDate)
      expect(xirr).toBeGreaterThanOrEqualTo(-10.0)
      expect(xirr).toBeLessThanOrEqualTo(10.0)
    }
  }

  @Test
  fun `XIRR returns zero for less than 2 transactions`() =
    runBlocking {
    val transactionArb = createXirrTransactionArb()
    checkAll(50, transactionArb) { transaction ->
      val xirr = xirrCalculationService.calculateAdjustedXirr(listOf(transaction), LocalDate.now())
      expect(xirr).toEqual(0.0)
    }
  }

  @Test
  fun `XIRR returns zero for empty transaction list`() {
    val xirr = xirrCalculationService.calculateAdjustedXirr(emptyList(), LocalDate.now())
    expect(xirr).toEqual(0.0)
  }

  @Test
  fun `XIRR transactions list contains final value when current value is positive`() =
    runBlocking {
    val portfolioTransactionArb = createPortfolioTransactionArb()
    val currentValueArb = Arb.bigDecimal(BigDecimal("100"), BigDecimal("100000"))
    checkAll(50, Arb.list(portfolioTransactionArb, 1..5), currentValueArb) { transactions, currentValue ->
      val calculationDate = LocalDate.now()
      val xirrTransactions =
        xirrCalculationService.buildXirrTransactions(
        transactions,
        currentValue,
        calculationDate,
      )
      val lastTransaction = xirrTransactions.lastOrNull()
      expect(lastTransaction != null).toEqual(true)
      expect(lastTransaction!!.amount > 0).toEqual(true)
      expect(lastTransaction.date).toEqual(calculationDate)
    }
  }

  @Test
  fun `buy transactions convert to negative XIRR amounts`() =
    runBlocking {
    val quantityArb = Arb.bigDecimal(BigDecimal("1"), BigDecimal("100"))
    val priceArb = Arb.bigDecimal(BigDecimal("10"), BigDecimal("500"))
    checkAll(100, quantityArb, priceArb) { quantity, price ->
      val buyTransaction =
        PortfolioTransaction(
        instrument = testInstrument,
        transactionType = TransactionType.BUY,
        quantity = quantity.setScale(2, RoundingMode.HALF_UP),
        price = price.setScale(2, RoundingMode.HALF_UP),
        transactionDate = LocalDate.now().minusDays(30),
        platform = Platform.LIGHTYEAR,
        commission = BigDecimal("0.50"),
      )
      val xirrTransaction = xirrCalculationService.convertToXirrTransaction(buyTransaction)
      expect(xirrTransaction.amount < 0).toEqual(true)
    }
  }

  @Test
  fun `sell transactions convert to positive XIRR amounts`() =
    runBlocking {
    val quantityArb = Arb.bigDecimal(BigDecimal("1"), BigDecimal("100"))
    val priceArb = Arb.bigDecimal(BigDecimal("10"), BigDecimal("500"))
    checkAll(100, quantityArb, priceArb) { quantity, price ->
      val sellTransaction =
        PortfolioTransaction(
        instrument = testInstrument,
        transactionType = TransactionType.SELL,
        quantity = quantity.setScale(2, RoundingMode.HALF_UP),
        price = price.setScale(2, RoundingMode.HALF_UP),
        transactionDate = LocalDate.now().minusDays(30),
        platform = Platform.LIGHTYEAR,
        commission = BigDecimal("0.50"),
      )
      val xirrTransaction = xirrCalculationService.convertToXirrTransaction(sellTransaction)
      expect(xirrTransaction.amount > 0).toEqual(true)
    }
  }

  @Test
  fun `XIRR calculation is stable across multiple runs`() =
    runBlocking {
    val transactions =
      listOf(
      Transaction(-1000.0, LocalDate.now().minusDays(365)),
      Transaction(-500.0, LocalDate.now().minusDays(180)),
      Transaction(1800.0, LocalDate.now()),
    )
    val results =
      (1..10).map {
      xirrCalculationService.calculateAdjustedXirr(transactions, LocalDate.now())
    }
    val first = results.first()
    results.forEach { result ->
      expect(result).toEqual(first)
    }
  }

  private fun createXirrTransactionArb(): Arb<Transaction> =
    Arb.double(-10000.0, 10000.0).let { amountArb ->
      Arb
        .localDate(
        LocalDate.now().minusYears(3),
        LocalDate.now(),
      ).let { dateArb ->
        io.kotest.property.arbitrary.arbitrary {
          Transaction(amountArb.bind(), dateArb.bind())
        }
      }
    }

  private fun createPortfolioTransactionArb(): Arb<PortfolioTransaction> =
    Arb.bigDecimal(BigDecimal("1"), BigDecimal("100")).let { quantityArb ->
      Arb.bigDecimal(BigDecimal("10"), BigDecimal("500")).let { priceArb ->
        Arb
          .localDate(
          LocalDate.of(2020, 1, 1),
          LocalDate.of(2024, 12, 31),
        ).let { dateArb ->
          Arb.int(1..100).let { idArb ->
            io.kotest.property.arbitrary.arbitrary {
              PortfolioTransaction(
                instrument = testInstrument,
                transactionType = TransactionType.BUY,
                quantity = quantityArb.bind().setScale(2, RoundingMode.HALF_UP),
                price = priceArb.bind().setScale(2, RoundingMode.HALF_UP),
                transactionDate = dateArb.bind(),
                platform = Platform.LIGHTYEAR,
                commission = BigDecimal("0.50"),
              ).apply { id = idArb.bind().toLong() }
            }
          }
        }
      }
    }
}
