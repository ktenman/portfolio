package ee.tenman.portfolio.service.calculation

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.transaction.TransactionService
import ee.tenman.portfolio.testing.fixture.TransactionFixtures
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

abstract class InvestmentMetricsTestBase {
  protected val clock: Clock = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneId.of("UTC"))
  protected val dailyPriceService = mockk<DailyPriceService>()
  protected val transactionService = mockk<TransactionService>()
  protected val xirrCalculationService = XirrCalculationService(clock)
  protected val holdingsCalculationService = HoldingsCalculationService()
  protected val testDate: LocalDate = TransactionFixtures.DEFAULT_DATE
  protected val testInstrument: Instrument = TransactionFixtures.createInstrument()
  protected val investmentMetricsService =
    InvestmentMetricsService(
      dailyPriceService,
      transactionService,
      xirrCalculationService,
      holdingsCalculationService,
      Clock.systemDefaultZone(),
    )

  @BeforeEach
  fun setUp() {
    every { transactionService.calculateTransactionProfits(any(), any()) } answers {
      val transactions = firstArg<List<PortfolioTransaction>>()
      transactions.forEach {
        it.unrealizedProfit = BigDecimal.ZERO
        it.realizedProfit = BigDecimal.ZERO
      }
    }
  }

  protected fun createBuyCashFlow(
    quantity: BigDecimal,
    price: BigDecimal,
    date: LocalDate = testDate,
    commission: BigDecimal = TransactionFixtures.DEFAULT_COMMISSION,
    platform: Platform = Platform.LHV,
    instrument: Instrument = testInstrument,
  ): PortfolioTransaction = TransactionFixtures.createBuyTransaction(instrument, quantity, price, date, platform, commission)

  protected fun createSellCashFlow(
    quantity: BigDecimal,
    price: BigDecimal,
    date: LocalDate = testDate,
    commission: BigDecimal = TransactionFixtures.DEFAULT_COMMISSION,
    platform: Platform = Platform.LHV,
  ): PortfolioTransaction = TransactionFixtures.createSellTransaction(testInstrument, quantity, price, date, platform, commission)
}
