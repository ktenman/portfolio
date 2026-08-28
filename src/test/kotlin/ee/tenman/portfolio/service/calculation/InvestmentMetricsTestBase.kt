package ee.tenman.portfolio.service.calculation

import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.transaction.TransactionService
import ee.tenman.portfolio.testing.fixture.CashFlowTestBase
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import java.math.BigDecimal
import java.time.Clock

abstract class InvestmentMetricsTestBase : CashFlowTestBase() {
  protected val dailyPriceService = mockk<DailyPriceService>()
  protected val transactionService = mockk<TransactionService>()
  private val xirrCalculationService = XirrCalculationService(clock)
  private val holdingsCalculationService = HoldingsCalculationService()
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
}
