package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@ExtendWith(MockitoExtension::class)
class PortfolioSummaryServiceTest {

  @Mock
  private lateinit var portfolioTransactionService: PortfolioTransactionService

  @Mock
  private lateinit var dailyPriceService: DailyPriceService

  @Mock
  private lateinit var unifiedProfitCalculationService: UnifiedProfitCalculationService

  @Mock
  private lateinit var investmentMetricsService: InvestmentMetricsService

  @Mock
  private lateinit var portfolioDailySummaryRepository: PortfolioDailySummaryRepository

  @Mock
  private lateinit var clock: Clock

  @InjectMocks
  private lateinit var portfolioSummaryService: PortfolioSummaryService

  @Test
  fun `getCurrentDaySummary should always reflect current instrument data`() {
    // Setup
    val testDate = LocalDate.of(2025, 5, 10)
    val fixedClock = Clock.fixed(
      testDate.atStartOfDay(ZoneId.systemDefault()).toInstant(),
      ZoneId.systemDefault()
    )
    whenever(clock.instant()).thenReturn(fixedClock.instant())
    whenever(clock.zone).thenReturn(fixedClock.zone)

    val instrument = Instrument(
      symbol = "QDVE:GER:EUR",
      name = "iShares S&P 500 Information Technology Sector",
      category = "ETF",
      baseCurrency = "EUR",
      currentPrice = BigDecimal("27.58")
    )

    val transaction = PortfolioTransaction(
      instrument = instrument,
      transactionType = TransactionType.BUY,
      quantity = BigDecimal("793.00"),
      price = BigDecimal("29.81"),
      transactionDate = testDate.minusDays(10),
      platform = Platform.TRADING212
    )

    // Initial metrics showing -973.77 profit (matching instruments page)
    val initialMetrics = InvestmentMetricsService.InstrumentMetrics(
      totalInvestment = BigDecimal("23645.33"),
      currentValue = BigDecimal("21870.94"),
      profit = BigDecimal("-973.77"),
      xirr = -0.1048,
      quantity = BigDecimal("793.00")
    )

    whenever(portfolioTransactionService.getAllTransactions()).thenReturn(listOf(transaction))
    whenever(investmentMetricsService.calculateInstrumentMetrics(instrument, listOf(transaction)))
      .thenReturn(initialMetrics)

    // Test initial state
    val initialSummary = portfolioSummaryService.getCurrentDaySummary()

    // Verify initial profit matches the instrument page
    assertThat(initialSummary.totalProfit.setScale(2, java.math.RoundingMode.HALF_UP))
      .isEqualByComparingTo(BigDecimal("-973.77"))

    // Now simulate a change in metrics (like what happens when instrument prices update)
    // These updated metrics show -953.43 (the value that was incorrectly showing on summary page)
    val updatedMetrics = InvestmentMetricsService.InstrumentMetrics(
      totalInvestment = BigDecimal("23645.33"),
      currentValue = BigDecimal("21870.94"),
      profit = BigDecimal("-953.43"),
      xirr = -0.1060,
      quantity = BigDecimal("793.00")
    )

    whenever(investmentMetricsService.calculateInstrumentMetrics(instrument, listOf(transaction)))
      .thenReturn(updatedMetrics)

    // Test again - with caching removed, this should now reflect the updated metrics
    val updatedSummary = portfolioSummaryService.getCurrentDaySummary()

    // Verify updated profit is reflected immediately after the change
    assertThat(updatedSummary.totalProfit.setScale(2, java.math.RoundingMode.HALF_UP))
      .isEqualByComparingTo(BigDecimal("-953.43"))
  }
}
