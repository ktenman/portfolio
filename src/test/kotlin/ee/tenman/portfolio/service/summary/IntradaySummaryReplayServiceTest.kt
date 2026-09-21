package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.DailyPricePoint
import ee.tenman.portfolio.domain.InstrumentMinutePrice
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioIntradaySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TimeRange
import ee.tenman.portfolio.repository.InstrumentMinutePriceRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.calculation.HoldingsCalculationService
import ee.tenman.portfolio.service.calculation.InvestmentMetricsService
import ee.tenman.portfolio.service.calculation.ProfitCalculationEngine
import ee.tenman.portfolio.service.calculation.XirrCalculationService
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.pricing.PriceLookup
import ee.tenman.portfolio.service.transaction.TransactionService
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.createBuyTransaction
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.createCashInstrument
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.createInstrument
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.createSellTransaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class IntradaySummaryReplayServiceTest {
  private val stock = createInstrument(currentPrice = BigDecimal("999"))
  private val selection = listOf(Platform.LIGHTYEAR, Platform.LIGHTYEAR_BUSINESS)
  private val repository = mockk<InstrumentMinutePriceRepository>()
  private val transactions = mockk<PortfolioTransactionRepository>()
  private val daily = mockk<DailyPriceService>()

  @Test
  fun `should replay changing captured prices instead of the live price`() {
    val service = service(listOf(price("2026-09-21T12:20:00Z", "110"), price("2026-09-21T12:24:00Z", "130")))
    val points = service.getPoints(TimeRange.ONE_DAY, selection)
    expect(points.map { it.totalValue.toInt() }).toEqual(listOf(1100, 1300, 1300, 1300))
  }

  @Test
  fun `should use the last whole minute of each epoch aligned bin`() {
    val service = service(listOf(price("2026-09-21T12:20:00Z", "110")))
    val points = service.getPoints(TimeRange.ONE_DAY, selection)
    expect(points.map { it.date }).toEqual(
      listOf("2026-09-21T12:23:00Z", "2026-09-21T12:28:00Z", "2026-09-21T12:33:00Z", "2026-09-21T12:34:00Z")
        .map(Instant::parse),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["1D", "1W"])
  fun `should cap a full window at three hundred points`(range: String) {
    val service = service(listOf(price("2026-09-01T00:00:00Z", "110")))
    expect(service.getPoints(TimeRange.from(range), selection)).toHaveSize(300)
  }

  @Test
  fun `should carry the latest price from before the window into the first point`() {
    val service = service(listOf(price("2026-09-19T12:00:00Z", "110"), price("2026-09-21T12:24:00Z", "130")))
    expect(service.getPoints(TimeRange.ONE_DAY, selection).first().totalValue).toEqualNumerically(BigDecimal("1100"))
  }

  @Test
  fun `should align weekly bins to two thousand and sixteen epoch seconds`() {
    val service = service(listOf(price("2026-09-21T11:00:00Z", "110")))
    expect(service.getPoints(TimeRange.ONE_WEEK, selection).map { it.date }).toEqual(
      listOf("2026-09-21T11:31:00Z", "2026-09-21T12:04:00Z", "2026-09-21T12:34:00Z").map(Instant::parse),
    )
  }

  @Test
  fun `should query only the selected instruments with the complete time window`() {
    val service = service(listOf(price("2026-09-21T12:20:00Z", "110")))
    service.getPoints(TimeRange.ONE_WEEK, selection)
    verify(exactly = 1) {
      repository.findForReplay(listOf(1L), Instant.parse("2026-09-14T12:34:56Z"), Instant.parse("2026-09-21T12:34:00Z"))
    }
  }

  @Test
  fun `should start short history at the first capture without inventing earlier points`() {
    val service = service(listOf(price("2026-09-21T12:34:00Z", "110")))
    expect(service.getPoints(TimeRange.ONE_WEEK, selection).map { it.date }).toEqual(listOf(Instant.parse("2026-09-21T12:34:00Z")))
  }

  @Test
  fun `should not duplicate the latest minute when now has crossed a bin boundary`() {
    val service = service(listOf(price("2026-09-21T12:20:00Z", "110")), now = "2026-09-21T12:33:56Z")
    expect(service.getPoints(TimeRange.ONE_DAY, selection).map { it.date }).toEqual(
      listOf("2026-09-21T12:23:00Z", "2026-09-21T12:28:00Z", "2026-09-21T12:33:00Z").map(Instant::parse),
    )
  }

  @Test
  fun `should return no points without transactions`() {
    val service = service(emptyList(), trades = emptyList())
    expect(service.getPoints(TimeRange.ONE_DAY, selection)).toBeEmpty()
  }

  @Test
  fun `should return no points before any selected instrument has a capture`() {
    expect(service(emptyList()).getPoints(TimeRange.ONE_DAY, selection)).toBeEmpty()
  }

  @Test
  fun `should leave monthly ranges on daily data`() {
    expect(service(emptyList()).getPoints(TimeRange.ONE_MONTH, selection)).toBeEmpty()
  }

  @Test
  fun `should include a trade dated today in every point of today`() {
    val service =
      service(
      listOf(price("2026-09-21T00:00:00Z", "110")),
      trades = listOf(buy(LocalDate.of(2026, 9, 21))),
    )
    expect(service.getPoints(TimeRange.ONE_DAY, selection).map { it.totalValue.toInt() }.distinct()).toEqual(listOf(1100))
  }

  @Test
  fun `should exclude trades dated after a point`() {
    val trades = listOf(buy(LocalDate.of(2026, 9, 1)), buy(LocalDate.of(2026, 9, 22)))
    val service = service(listOf(price("2026-09-21T12:20:00Z", "110")), trades = trades)
    expect(service.getPoints(TimeRange.ONE_DAY, selection).last().totalValue).toEqualNumerically(BigDecimal("1100"))
  }

  @Test
  fun `should switch transaction eligibility and earnings age at Tallinn midnight`() {
    val trades = listOf(buy(LocalDate.of(2026, 9, 19)), buy(LocalDate.of(2026, 9, 21), "5"))
    val service = service(listOf(price("2026-09-20T20:40:00Z", "110")), trades, "2026-09-20T21:10:56Z")
    val points = service.getPoints(TimeRange.ONE_DAY, selection)
    expect(points.first().totalValue).toEqualNumerically(BigDecimal("1100"))
    expect(points.first().earningsPerMonth).toEqualNumerically(BigDecimal("3043.75"))
    expect(points.last().totalValue).toEqualNumerically(BigDecimal("1650"))
    expect(points.last().earningsPerMonth).toEqualNumerically(BigDecimal("2282.8125"))
  }

  @Test
  fun `should advance the daily annual baseline at Tallinn midnight`() {
    val dailyPrices =
      listOf(
      DailyPricePoint(1L, LocalDate.of(2025, 9, 20), BigDecimal("110")),
      DailyPricePoint(1L, LocalDate.of(2025, 9, 21), BigDecimal("120")),
    )
    val service =
      service(
      listOf(price("2026-09-20T20:40:00Z", "130")),
      listOf(buy(LocalDate.of(2025, 9, 1))),
      "2026-09-20T21:10:56Z",
      dailyPrices,
    )
    val points = service.getPoints(TimeRange.ONE_DAY, selection)
    expect(points.first().earningsPerMonth).toEqualNumerically(BigDecimal("16.67808219240625"))
    expect(points.last().earningsPerMonth).toEqualNumerically(BigDecimal("8.33904109468125"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["2026-03-29", "2026-10-25"])
  fun `should keep prices and timestamps ordered through Tallinn daylight saving transitions`(date: String) {
    val service =
      service(
      listOf(price("${date}T00:40:00Z", "110"), price("${date}T01:00:00Z", "130")),
      trades = listOf(buy(LocalDate.parse(date))),
      now = "${date}T01:10:56Z",
    )
    val points = service.getPoints(TimeRange.ONE_DAY, selection)
    expect(points.map { it.date }.distinct().sorted()).toEqual(points.map { it.date })
    expect(points.first().totalValue).toEqualNumerically(BigDecimal("1100"))
    expect(points.last().totalValue).toEqualNumerically(BigDecimal("1300"))
  }

  @Test
  fun `should use daily prices for an instrument without a captured price`() {
    val other = createInstrument(id = 2L, symbol = "MSFT").apply { currentPrice = null }
    val trades = listOf(buy(LocalDate.of(2026, 9, 1)), createBuyTransaction(other, BigDecimal.ONE, BigDecimal.TEN))
    val prices = listOf(DailyPricePoint(2L, LocalDate.of(2026, 9, 20), BigDecimal("77")))
    val service = service(listOf(price("2026-09-20T12:20:00Z", "110")), trades, "2026-09-20T12:34:56Z", prices)
    expect(service.getPoints(TimeRange.ONE_DAY, selection).last().totalValue).toEqualNumerically(BigDecimal("1177"))
  }

  @Test
  fun `should match the canonical calculator including the daily annual baseline`() {
    val date = LocalDate.of(2026, 9, 21)
    val trades = listOf(buy(date.minusDays(400)))
    val dailyPrices = listOf(DailyPricePoint(1L, date.minusDays(365), BigDecimal("110")))
    val service = service(listOf(price("2026-09-21T12:20:00Z", "130")), trades, dailyPrices = dailyPrices)
    val point = service.getPoints(TimeRange.ONE_DAY, selection).last()
    val lookup = PriceLookup(dailyPrices).pinnedAt(date, mapOf(1L to BigDecimal("130")))
    val summary = calculator(clock()).calculateFromTransactions(trades, date, lookup)
    expect(point.totalValue).toEqualNumerically(summary.totalValue)
    expect(point.totalProfit).toEqualNumerically(BigDecimal("300"))
    expect(point.xirrAnnualReturn).toEqualNumerically(summary.xirrAnnualReturn)
    expect(point.earningsPerMonth).toEqualNumerically(summary.toSummaryDto().earningsPerMonth)
  }

  @Test
  fun `should match a stored single platform snapshot at fixed prices`() {
    stock.currentPrice = BigDecimal("110")
    val trades = listOf(buy(LocalDate.of(2026, 9, 1)))
    val service = service(listOf(price("2026-09-21T12:20:00Z", "110")), trades)
    val point = service.getPoints(TimeRange.ONE_DAY, listOf(Platform.LIGHTYEAR)).last()
    val summary = calculator(clock()).calculateFromTransactions(trades, LocalDate.of(2026, 9, 21))
    val stored =
      PortfolioIntradaySummary(
        point.date,
        "LIGHTYEAR",
        summary.totalValue,
        summary.xirrAnnualReturn,
        summary.totalProfit,
        summary.earningsPerDay,
      )
    expect(point).toEqual(stored.toIntradayPointDto())
  }

  @Test
  fun `should calculate a shared instrument and net zero cash canonically across platforms`() {
    stock.currentPrice = BigDecimal.ZERO
    val cash = createCashInstrument(id = 2L)
    val date = LocalDate.of(2026, 9, 1)
    val trades =
      listOf(
      buy(date, "1"),
      createBuyTransaction(stock, BigDecimal.ONE, BigDecimal("50"), date, Platform.LIGHTYEAR_BUSINESS, BigDecimal.ZERO),
      createSellTransaction(stock, BigDecimal.ONE, BigDecimal("60"), date, Platform.LIGHTYEAR_BUSINESS, BigDecimal.ZERO),
      createBuyTransaction(cash, BigDecimal("100"), BigDecimal.ONE, date, Platform.LIGHTYEAR, BigDecimal.ZERO),
      createSellTransaction(cash, BigDecimal("100"), BigDecimal.ONE, date, Platform.LIGHTYEAR, BigDecimal.ZERO),
      createBuyTransaction(cash, BigDecimal("100"), BigDecimal.ONE, date, Platform.LIGHTYEAR_BUSINESS, BigDecimal.ZERO),
    )
    val captures = listOf(InstrumentMinutePrice(cash, Instant.parse("2026-09-20T12:20:00Z"), BigDecimal.ONE))
    val dailyPrices = listOf(DailyPricePoint(1L, LocalDate.of(2026, 9, 20), BigDecimal.ZERO))
    val service = service(captures, trades, "2026-09-20T12:34:56Z", dailyPrices)
    expect(service.getPoints(TimeRange.ONE_DAY, selection).last().totalProfit).toEqualNumerically(BigDecimal("-90"))
  }

  @Test
  fun `should normalize platform order and duplicates for caching`() {
    val service = service(emptyList())
    expect(service.selectionKey(listOf(Platform.LIGHTYEAR_BUSINESS, Platform.LIGHTYEAR, Platform.LIGHTYEAR)))
      .toEqual("LIGHTYEAR,LIGHTYEAR_BUSINESS")
  }

  private fun service(
    captures: List<InstrumentMinutePrice>,
    trades: List<PortfolioTransaction> = listOf(buy(LocalDate.of(2026, 9, 1))),
    now: String = "2026-09-21T12:34:56Z",
    dailyPrices: List<DailyPricePoint> = emptyList(),
  ): IntradaySummaryReplayService {
    every { transactions.findAllByPlatformsWithInstruments(any()) } returns trades
    every { repository.findForReplay(any(), any(), any()) } returns captures
    every { daily.buildPriceLookup(any()) } returns PriceLookup(dailyPrices)
    return IntradaySummaryReplayService(transactions, repository, daily, calculator(clock(now)), clock(now))
  }

  private fun calculator(clock: Clock): DailySummaryCalculator {
    val transactionService = TransactionService(transactions, ProfitCalculationEngine(), mockk(), clock)
    val xirr = XirrCalculationService(clock)
    val metrics = InvestmentMetricsService(daily, transactionService, xirr, HoldingsCalculationService(), clock)
    return DailySummaryCalculator(metrics, xirr)
  }

  private fun clock(now: String = "2026-09-21T12:34:56Z"): Clock = Clock.fixed(Instant.parse(now), ZoneId.of("Europe/Tallinn"))

  private fun price(
    at: String,
    value: String,
  ): InstrumentMinutePrice = InstrumentMinutePrice(stock, Instant.parse(at), BigDecimal(value))

  private fun buy(
    date: LocalDate,
    quantity: String = "10",
  ): PortfolioTransaction = createBuyTransaction(stock, BigDecimal(quantity), BigDecimal("100"), date, Platform.LIGHTYEAR, BigDecimal.ZERO)
}
