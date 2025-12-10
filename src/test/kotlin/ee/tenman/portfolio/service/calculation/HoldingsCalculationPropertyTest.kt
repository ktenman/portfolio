package ee.tenman.portfolio.service.calculation

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigDecimal
import io.kotest.property.arbitrary.enum
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

class HoldingsCalculationPropertyTest {
  private lateinit var holdingsCalculationService: HoldingsCalculationService
  private lateinit var testInstrument: Instrument

  @BeforeEach
  fun setUp() {
    holdingsCalculationService = HoldingsCalculationService()
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
  fun `net position equals buy quantity minus sell quantity`() =
    runBlocking {
    val transactionArb =
      Arb.list(
      createTransactionArb(),
      1..20,
    )
    checkAll(100, transactionArb) { transactions ->
      val buyQuantity =
        transactions
        .filter { it.transactionType == TransactionType.BUY }
        .sumOf { it.quantity }
      val sellQuantity =
        transactions
        .filter { it.transactionType == TransactionType.SELL }
        .sumOf { it.quantity }
      val expectedNetPosition = buyQuantity.subtract(sellQuantity)
      val actualNetPosition = holdingsCalculationService.calculateNetQuantity(transactions)
      expect(actualNetPosition).toEqualNumerically(expectedNetPosition)
    }
  }

  @Test
  fun `holdings quantity is never negative when sells do not exceed buys`() =
    runBlocking {
    val transactionArb =
      Arb.list(
      createBuyOnlyTransactionArb(),
      1..10,
    )
    checkAll(100, transactionArb) { transactions ->
      val (quantity, _) = holdingsCalculationService.calculateCurrentHoldings(transactions)
      expect(quantity).toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
    }
  }

  @Test
  fun `average cost is positive when holdings are positive`() =
    runBlocking {
    val transactionArb =
      Arb.list(
      createBuyOnlyTransactionArb(),
      1..10,
    )
    checkAll(100, transactionArb) { transactions ->
      val (quantity, averageCost) = holdingsCalculationService.calculateCurrentHoldings(transactions)
      if (quantity > BigDecimal.ZERO) {
        expect(averageCost > BigDecimal.ZERO).toEqual(true)
      }
    }
  }

  @Test
  fun `current value equals holdings multiplied by price`() =
    runBlocking {
    val holdingsArb = Arb.bigDecimal(BigDecimal("0.01"), BigDecimal("10000"))
    val priceArb = Arb.bigDecimal(BigDecimal("0.01"), BigDecimal("10000"))
    checkAll(100, holdingsArb, priceArb) { holdings, price ->
      val expectedValue = holdings.multiply(price)
      val actualValue = holdingsCalculationService.calculateCurrentValue(holdings, price)
      expect(actualValue.setScale(10, RoundingMode.HALF_UP))
        .toEqualNumerically(expectedValue.setScale(10, RoundingMode.HALF_UP))
    }
  }

  @Test
  fun `profit calculation is consistent`() =
    runBlocking {
    val holdingsArb = Arb.bigDecimal(BigDecimal("1"), BigDecimal("1000"))
    val avgCostArb = Arb.bigDecimal(BigDecimal("10"), BigDecimal("500"))
    val currentPriceArb = Arb.bigDecimal(BigDecimal("10"), BigDecimal("500"))
    checkAll(100, holdingsArb, avgCostArb, currentPriceArb) { holdings, avgCost, currentPrice ->
      val profit = holdingsCalculationService.calculateProfit(holdings, avgCost, currentPrice)
      val expectedProfit = holdings.multiply(currentPrice).subtract(holdings.multiply(avgCost))
      expect(profit.setScale(10, RoundingMode.HALF_UP))
        .toEqualNumerically(expectedProfit.setScale(10, RoundingMode.HALF_UP))
    }
  }

  private fun createTransactionArb(): Arb<PortfolioTransaction> =
    Arb.bigDecimal(BigDecimal("1"), BigDecimal("100")).let { quantityArb ->
      Arb.bigDecimal(BigDecimal("10"), BigDecimal("500")).let { priceArb ->
        Arb.enum<TransactionType>().let { typeArb ->
          Arb
            .localDate(
            LocalDate.of(2020, 1, 1),
            LocalDate.of(2024, 12, 31),
          ).let { dateArb ->
            Arb.int(1..100).let { idArb ->
              io.kotest.property.arbitrary.arbitrary {
                PortfolioTransaction(
                  instrument = testInstrument,
                  transactionType = typeArb.bind(),
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

  private fun createBuyOnlyTransactionArb(): Arb<PortfolioTransaction> =
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
