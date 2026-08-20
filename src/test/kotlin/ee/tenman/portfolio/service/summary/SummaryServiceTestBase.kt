package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.model.metrics.PortfolioMetrics
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import ee.tenman.portfolio.service.calculation.InvestmentMetricsService
import ee.tenman.portfolio.service.calculation.XirrCalculationService
import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import ee.tenman.portfolio.service.transaction.TransactionService
import ee.tenman.portfolio.testing.fixture.TransactionFixtures
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.springframework.cache.CacheManager
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

abstract class SummaryServiceTestBase {
  protected val transactionService = mockk<TransactionService>()
  protected val portfolioDailySummaryRepository = mockk<PortfolioDailySummaryRepository>(relaxUnitFun = true)
  protected val cacheManager = mockk<CacheManager>(relaxed = true)
  protected val investmentMetricsService = mockk<InvestmentMetricsService>()
  protected val xirrCalculationService = mockk<XirrCalculationService>()
  protected val clock = mockk<Clock>()
  protected val summaryBatchProcessor = mockk<SummaryBatchProcessorService>(relaxed = true)
  protected val summaryDeletionService = mockk<SummaryDeletionService>(relaxed = true)
  protected val summaryCacheService = mockk<SummaryCacheService>(relaxed = true)
  protected val dailySummaryCalculator = DailySummaryCalculator(investmentMetricsService, xirrCalculationService)
  protected val summaryService =
    SummaryService(
      portfolioDailySummaryRepository,
      transactionService,
      cacheManager,
      clock,
      summaryBatchProcessor,
      summaryDeletionService,
      summaryCacheService,
      dailySummaryCalculator,
    )
  protected val testDate: LocalDate = LocalDate.of(2025, 5, 10)
  protected val instrument: Instrument =
    TransactionFixtures.createInstrument(
      symbol = "QDVE:GER:EUR",
      name = "iShares S&P 500 Information Technology Sector",
      category = "ETF",
      baseCurrency = "EUR",
      currentPrice = BigDecimal("27.58"),
    )

  @BeforeEach
  fun setup() {
    every { clock.instant() } returns testDate.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
    every { clock.zone } returns ZoneId.systemDefault()
    every { portfolioDailySummaryRepository.findByEntryDate(any()) } returns null
    every { investmentMetricsService.calculatePortfolioMetrics(any(), any(), any()) } returns
      PortfolioMetrics(totalValue = BigDecimal.ZERO, totalProfit = BigDecimal.ZERO)
    every { xirrCalculationService.buildCashFlows(any(), any(), any()) } returns emptyList()
    every { xirrCalculationService.calculateAdjustedXirr(any(), any()) } returns 0.0
  }

  protected fun stubMetrics(
    date: LocalDate,
    totalValue: BigDecimal,
    totalProfit: BigDecimal,
    vararg cashFlows: CashFlow,
  ) {
    every { investmentMetricsService.calculatePortfolioMetrics(any(), date) } returns
      PortfolioMetrics(totalValue = totalValue, totalProfit = totalProfit).apply {
        xirrCashFlows.addAll(cashFlows)
      }
  }

  protected fun createBuyTransaction(
    quantity: BigDecimal,
    price: BigDecimal,
    date: LocalDate,
    platform: Platform = Platform.TRADING212,
    instrument: Instrument = this.instrument,
  ): PortfolioTransaction =
    TransactionFixtures.createBuyTransaction(instrument, quantity, price, date, platform, TransactionFixtures.ZERO_COMMISSION)

  protected fun expectedEarningsPerDay(
    totalValue: BigDecimal,
    annualReturn: BigDecimal,
  ): BigDecimal = totalValue.multiply(annualReturn).divide(BigDecimal("365.25"), 10, RoundingMode.HALF_UP)

  protected fun createSummary(
    date: LocalDate,
    totalValue: String,
    xirrAnnualReturn: String,
    totalProfit: String,
    earningsPerDay: String,
  ): PortfolioDailySummary =
    PortfolioDailySummary(
      entryDate = date,
      totalValue = BigDecimal(totalValue),
      xirrAnnualReturn = BigDecimal(xirrAnnualReturn),
      totalProfit = BigDecimal(totalProfit),
      earningsPerDay = BigDecimal(earningsPerDay),
    )
}
