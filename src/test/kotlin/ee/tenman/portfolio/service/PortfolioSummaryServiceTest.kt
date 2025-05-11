package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@ExtendWith(MockitoExtension::class)
class PortfolioSummaryServiceTest {

  @Mock
  private lateinit var portfolioTransactionService: PortfolioTransactionService
  @Mock
  private lateinit var dailyPriceService: DailyPriceService
  @Mock
  private lateinit var unifiedProfitCalculationService: UnifiedProfitCalculationService
  @Mock
  private lateinit var portfolioDailySummaryRepository: PortfolioDailySummaryRepository
  @Mock
  private lateinit var clock: Clock

  @InjectMocks
  private lateinit var portfolioSummaryService: PortfolioSummaryService

  private lateinit var testDate: LocalDate
  private lateinit var instrument: Instrument
  private lateinit var transaction: PortfolioTransaction

  @BeforeEach
  fun setup() {
    testDate = LocalDate.of(2025, 5, 10)
    instrument = Instrument(
      symbol = "QDVE:GER:EUR",
      name = "iShares S&P 500 Information Technology Sector",
      category = "ETF",
      baseCurrency = "EUR",
      currentPrice = BigDecimal("27.58")
    )
    transaction = PortfolioTransaction(
      instrument = instrument,
      transactionType = TransactionType.BUY,
      quantity = BigDecimal("793.00"),
      price = BigDecimal("29.81"),
      transactionDate = testDate.minusDays(10),
      platform = Platform.TRADING212
    )
  }

  @Test
  fun `getCurrentDaySummary should always reflect current instrument data`() {
    val fixedInstant = ZonedDateTime.of(2025, 5, 10, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant()
    whenever(clock.instant()).thenReturn(fixedInstant)
    whenever(clock.zone).thenReturn(ZoneId.systemDefault())

    val initialMetricsValue = BigDecimal("21870.94")
    val initialXirr = -0.1048

    whenever(portfolioTransactionService.getAllTransactions()).thenReturn(listOf(transaction))
    val initPrice = initialMetricsValue.divide(transaction.quantity, 10, RoundingMode.HALF_UP)
    whenever(dailyPriceService.getPrice(eq(instrument), any())).thenReturn(initPrice)
    whenever(unifiedProfitCalculationService.calculateAdjustedXirr(any(), any()))
      .thenReturn(initialXirr)

    val summary = portfolioSummaryService.getCurrentDaySummary()
    assertThat(summary.totalProfit).isEqualByComparingTo(
      initialMetricsValue.subtract(transaction.price.multiply(transaction.quantity))
    )
    assertThat(summary.earningsPerDay).isEqualByComparingTo("-6.2753580068")
  }

  @Test
  fun `calculateSummaryForDate should correctly calculate earnings per day based on XIRR and total value`() {
    val specificDate = LocalDate.of(2024, 7, 1)
    val totalValue = BigDecimal("100.46")
    val xirrRate = 0.00455647

    val price = totalValue.divide(transaction.quantity, 10, RoundingMode.HALF_UP)
    val datedTxn = PortfolioTransaction(
      instrument = instrument,
      transactionType = transaction.transactionType,
      quantity = transaction.quantity,
      price = price,
      transactionDate = specificDate.minusDays(10),
      platform = transaction.platform
    )

    whenever(portfolioTransactionService.getAllTransactions())
      .thenReturn(listOf(datedTxn))
    whenever(dailyPriceService.getPrice(eq(instrument), eq(specificDate)))
      .thenReturn(price)
    whenever(unifiedProfitCalculationService.calculateAdjustedXirr(any(), any()))
      .thenReturn(xirrRate)

    val summary = portfolioSummaryService.calculateSummaryForDate(specificDate)

    assertThat(summary.totalValue)
      .isEqualByComparingTo("100.4600000365")
    assertThat(summary.totalProfit)
      .isEqualByComparingTo(BigDecimal("0.00"))
    assertThat(summary.xirrAnnualReturn)
      .isEqualByComparingTo(BigDecimal(xirrRate).setScale(8, RoundingMode.HALF_UP))

    assertThat(summary.earningsPerDay)
      .isEqualByComparingTo("0.0012532320")
  }

  @Test
  fun `calculateSummaryForDate should handle zero XIRR and return zero earnings per day`() {
    val zeroDate = LocalDate.of(2024, 7, 1)
    val price = BigDecimal("123.45")
    val datedTxn = PortfolioTransaction(
      instrument = instrument,
      transactionType = transaction.transactionType,
      quantity = transaction.quantity,
      price = price,
      transactionDate = zeroDate.minusDays(10),
      platform = transaction.platform
    )

    whenever(portfolioTransactionService.getAllTransactions())
      .thenReturn(listOf(datedTxn))
    whenever(dailyPriceService.getPrice(eq(instrument), eq(zeroDate)))
      .thenReturn(price)
    whenever(unifiedProfitCalculationService.calculateAdjustedXirr(any(), any()))
      .thenReturn(0.0)

    val summary = portfolioSummaryService.calculateSummaryForDate(zeroDate)
    assertThat(summary.totalProfit).isEqualByComparingTo(BigDecimal("0.00"))
    assertThat(summary.earningsPerDay).isEqualByComparingTo(BigDecimal("0.00"))
  }
}
