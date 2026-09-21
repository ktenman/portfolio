package ee.tenman.portfolio.service.calculation

import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.DailyPricePoint
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.pricing.PriceLookup
import ee.tenman.portfolio.service.transaction.TransactionService
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.createBuyTransaction
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.createCashInstrument
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.createInstrument
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PortfolioPinnedPricesTest {
  private val clock = Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneId.of("Europe/Tallinn"))
  private val today = LocalDate.now(clock)

  @ParameterizedTest
  @ValueSource(longs = [0, 1])
  fun `should value holdings at their pinned price for today and past dates`(daysAgo: Long) {
    val date = today.minusDays(daysAgo)
    val instrument = createInstrument(currentPrice = BigDecimal("999"), id = 1L)
    val lookup = PriceLookup(listOf(DailyPricePoint(1L, date, BigDecimal("150"))))
    val pinned = lookup.pinnedAt(date, mapOf(1L to BigDecimal("123.45")))
    expect(value(instrument, date, pinned)).toEqualNumerically(BigDecimal("1234.50"))
  }

  @Test
  fun `should retain the live price for an unpinned instrument today`() {
    val instrument = createInstrument(currentPrice = BigDecimal("160"), id = 1L)
    val pinned = PriceLookup(emptyList()).pinnedAt(today, mapOf(2L to BigDecimal("123.45")))
    expect(value(instrument, today, pinned)).toEqualNumerically(BigDecimal("1600"))
  }

  @Test
  fun `should retain the live price when the lookup has no pins`() {
    val instrument = createInstrument(currentPrice = BigDecimal("160"), id = 1L)
    val lookup = PriceLookup(listOf(DailyPricePoint(1L, today, BigDecimal("150"))))
    expect(value(instrument, today, lookup)).toEqualNumerically(BigDecimal("1600"))
  }

  @Test
  fun `should preserve the cash unit price despite a captured price`() {
    val instrument = createCashInstrument().apply { id = 1L }
    val pinned = PriceLookup(emptyList()).pinnedAt(today, mapOf(1L to BigDecimal("123.45")))
    expect(value(instrument, today, pinned)).toEqualNumerically(BigDecimal("10"))
  }

  @Test
  fun `should use the pinned price when holdings calculation falls back`() {
    val instrument = createInstrument(currentPrice = BigDecimal("999"), id = 1L)
    val pinned = PriceLookup(emptyList()).pinnedAt(today, mapOf(1L to BigDecimal("123.45")))
    val holdings = spyk(HoldingsCalculationService())
    every { holdings.calculateAggregatedHoldings(any()) } throws IllegalStateException("Invalid aggregated holdings")
    expect(value(instrument, today, pinned, holdings)).toEqualNumerically(BigDecimal("1234.50"))
  }

  private fun value(
    instrument: Instrument,
    date: LocalDate,
    lookup: PriceLookup,
    holdings: HoldingsCalculationService = HoldingsCalculationService(),
  ): BigDecimal {
    val transaction = createBuyTransaction(instrument = instrument, quantity = BigDecimal.TEN, price = BigDecimal("100"))
    val transactions = mockk<TransactionService>()
    every { transactions.calculateTransactionProfits(any(), any()) } answers {
      ProfitCalculationEngine().calculateProfitsForPlatform(firstArg<List<PortfolioTransaction>>(), BigDecimal.ZERO)
    }
    val service = InvestmentMetricsService(mockk<DailyPriceService>(), transactions, XirrCalculationService(clock), holdings, clock)
    return service.calculatePortfolioMetrics(mapOf(instrument to listOf(transaction)), date, lookup).totalValue
  }
}
