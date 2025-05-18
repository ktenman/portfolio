package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cache.CacheManager
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
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
  private lateinit var instrumentService: InstrumentService

  @Mock
  private lateinit var cacheManager: CacheManager

  @Mock
  private lateinit var clock: Clock

  @InjectMocks
  private lateinit var portfolioSummaryService: PortfolioSummaryService

  @Captor
  private lateinit var summaryCaptor: ArgumentCaptor<PortfolioDailySummary>

  @Captor
  private lateinit var summaryListCaptor: ArgumentCaptor<List<PortfolioDailySummary>>

  private lateinit var testDate: LocalDate
  private lateinit var instrument: Instrument
  private lateinit var transaction: PortfolioTransaction

  @BeforeEach
  fun setup() {
    testDate = LocalDate.of(2025, 5, 10)

    val fixedInstant = ZonedDateTime.of(2025, 5, 10, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant()
    lenient().whenever(clock.instant()).thenReturn(fixedInstant)
    lenient().whenever(clock.zone).thenReturn(ZoneId.systemDefault())

    instrument = Instrument(
      symbol = "QDVE:GER:EUR",
      name = "iShares S&P 500 Information Technology Sector",
      category = "ETF",
      baseCurrency = "EUR",
      currentPrice = BigDecimal("27.58")
    ).apply {
      id = 1L
      currentValue = BigDecimal("21870.94")
      totalInvestment = BigDecimal("23633.33")
      profit = BigDecimal("-1762.39")
    }

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
    val fixedInstant = testDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
    whenever(clock.instant()).thenReturn(fixedInstant)

    val todaySummary = PortfolioDailySummary(
      entryDate = testDate,
      totalValue = BigDecimal("21000.00"),
      xirrAnnualReturn = BigDecimal("-0.1048"),
      totalProfit = BigDecimal("-2500.00"),
      earningsPerDay = BigDecimal("-6.0000000000")
    )
    whenever(portfolioDailySummaryRepository.findByEntryDate(testDate)).thenReturn(todaySummary)

    whenever(instrumentService.getAllInstruments()).thenReturn(
      listOf(
        instrument.apply {
          profit = BigDecimal("-1762.39")
          totalInvestment = BigDecimal("23633.33")
        }
      )
    )

    val summary = portfolioSummaryService.getCurrentDaySummary()

    assertThat(summary.totalProfit)
      .isEqualByComparingTo("-1762.39")
    assertThat(summary.earningsPerDay)
      .isEqualByComparingTo(BigDecimal("0E-10"))
  }

  @Test
  fun `calculateSummaryForDate should correctly calculate earnings per day based on XIRR and total value`() {
    val date = LocalDate.of(2024, 7, 1)
    val totalValue = BigDecimal("100.46")
    val xirr = 0.00455647

    val price = totalValue.divide(transaction.quantity, 10, RoundingMode.HALF_UP)
    val transaction = PortfolioTransaction(
      instrument = instrument,
      transactionType = this.transaction.transactionType,
      quantity = this.transaction.quantity,
      price = price,
      transactionDate = date.minusDays(10),
      platform = this.transaction.platform
    )

    whenever(portfolioTransactionService.getAllTransactions()).thenReturn(listOf(transaction))
    whenever(dailyPriceService.getPrice(eq(instrument), eq(date))).thenReturn(price)
    whenever(unifiedProfitCalculationService.calculateCurrentHoldings(any()))
      .thenReturn(transaction.quantity to price)

    // Fix: Use the three-parameter version of calculateAdjustedXirr that includes date
    whenever(unifiedProfitCalculationService.calculateAdjustedXirr(any(), any(), eq(date))).thenReturn(xirr)

    val summary = portfolioSummaryService.calculateSummaryForDate(date)

    // Calculate expected earnings per day: totalValue * xirrAnnualReturn / 365.25
    val expectedEarningsPerDay = totalValue
      .multiply(BigDecimal(xirr))
      .divide(BigDecimal("365.25"), 10, RoundingMode.HALF_UP)

    assertThat(summary.totalValue)
      .isEqualByComparingTo(BigDecimal("100.4600000365"))
    assertThat(summary.totalProfit)
      .isEqualByComparingTo(BigDecimal.ZERO)
    assertThat(summary.xirrAnnualReturn)
      .isEqualByComparingTo(BigDecimal(xirr).setScale(8, RoundingMode.HALF_UP))
    assertThat(summary.earningsPerDay)
      .isEqualByComparingTo(expectedEarningsPerDay)
  }

  @Test
  fun `calculateSummaryForDate should handle zero XIRR and return zero earnings per day`() {
    val date = LocalDate.of(2024, 7, 1)
    val price = BigDecimal("123.45")
    val transaction = PortfolioTransaction(
      instrument = instrument,
      transactionType = this.transaction.transactionType,
      quantity = this.transaction.quantity,
      price = price,
      transactionDate = date.minusDays(10),
      platform = this.transaction.platform
    )

    whenever(portfolioTransactionService.getAllTransactions()).thenReturn(listOf(transaction))
    whenever(dailyPriceService.getPrice(eq(instrument), eq(date))).thenReturn(price)
    whenever(unifiedProfitCalculationService.calculateCurrentHoldings(any()))
      .thenReturn(transaction.quantity to price)

    // Fix: Use the three-parameter version of calculateAdjustedXirr that includes date
    whenever(unifiedProfitCalculationService.calculateAdjustedXirr(any(), any(), eq(date))).thenReturn(0.0)

    val summary = portfolioSummaryService.calculateSummaryForDate(date)

    assertThat(summary.xirrAnnualReturn)
      .isEqualByComparingTo(BigDecimal.ZERO)
    assertThat(summary.totalProfit)
      .isEqualByComparingTo(BigDecimal.ZERO)
    assertThat(summary.earningsPerDay)
      .isEqualByComparingTo(BigDecimal.ZERO)
  }

  @Test
  fun `calculateSummaryForDate should return zero values when no transactions exist`() {
    val date = LocalDate.of(2024, 7, 1)
    whenever(portfolioTransactionService.getAllTransactions()).thenReturn(emptyList())

    val summary = portfolioSummaryService.calculateSummaryForDate(date)

    assertThat(summary.entryDate)
      .isEqualTo(date)
    assertThat(summary.totalValue)
      .isEqualByComparingTo(BigDecimal.ZERO)
    assertThat(summary.xirrAnnualReturn)
      .isEqualByComparingTo(BigDecimal.ZERO)
    assertThat(summary.totalProfit)
      .isEqualByComparingTo(BigDecimal.ZERO)
    assertThat(summary.earningsPerDay)
      .isEqualByComparingTo(BigDecimal.ZERO)
  }

  @Test
  fun `calculateSummaryForDate should fall back to legacy calculation when unified service fails`() {
    val date = LocalDate.of(2024, 7, 1)
    val price = BigDecimal("123.45")
    val transaction = PortfolioTransaction(
      instrument = instrument,
      transactionType = this.transaction.transactionType,
      quantity = BigDecimal("10"),
      price = BigDecimal("100"),
      transactionDate = date.minusDays(10),
      platform = this.transaction.platform
    )

    whenever(portfolioTransactionService.getAllTransactions()).thenReturn(listOf(transaction))
    whenever(dailyPriceService.getPrice(eq(instrument), eq(date))).thenReturn(price)

    // Throw exception to force fallback calculation path
    whenever(unifiedProfitCalculationService.calculateCurrentHoldings(any()))
      .thenThrow(RuntimeException("fail"))

    // Fix: Use the three-parameter version of calculateAdjustedXirr that includes date
    whenever(unifiedProfitCalculationService.calculateAdjustedXirr(any(), any(), eq(date))).thenReturn(0.05)

    val summary = portfolioSummaryService.calculateSummaryForDate(date)

    // The fallback calculation should use the standard formulas:
    // - totalValue = quantity * price
    // - totalProfit = totalValue - (quantity * original_price)
    val expectedTotal = price.multiply(transaction.quantity)
    val expectedProfit = expectedTotal.subtract(transaction.price.multiply(transaction.quantity))

    assertThat(summary.totalValue)
      .isEqualByComparingTo(expectedTotal)
    assertThat(summary.totalProfit)
      .isEqualByComparingTo(expectedProfit)

    // Verify the earnings are calculated correctly even when using the fallback path
    val expectedEarningsPerDay = expectedTotal
      .multiply(BigDecimal("0.05"))
      .divide(BigDecimal("365.25"), 10, RoundingMode.HALF_UP)

    assertThat(summary.earningsPerDay)
      .isEqualByComparingTo(expectedEarningsPerDay)
  }

  @Test
  fun `deleteAllDailySummaries should delete all summaries`() {
    portfolioSummaryService.deleteAllDailySummaries()

    verify(portfolioDailySummaryRepository, times(1)).deleteAll()
  }

  @Test
  fun `getAllDailySummaries should return all summaries`() {
    val summaries = listOf(
      PortfolioDailySummary(
        LocalDate.of(2024, 7, 1),
        BigDecimal("100"),
        BigDecimal("0.05"),
        BigDecimal("10"),
        BigDecimal("0.01")
      ),
      PortfolioDailySummary(
        LocalDate.of(2024, 7, 2),
        BigDecimal("110"),
        BigDecimal("0.06"),
        BigDecimal("20"),
        BigDecimal("0.02")
      )
    )
    whenever(portfolioDailySummaryRepository.findAll()).thenReturn(summaries)

    val result = portfolioSummaryService.getAllDailySummaries()

    assertThat(result)
      .hasSize(2)
      .isEqualTo(summaries)
  }

  @Test
  fun `getAllDailySummaries with paging should return paged summaries`() {
    val summaries = listOf(
      PortfolioDailySummary(
        LocalDate.of(2024, 7, 2),
        BigDecimal("110"),
        BigDecimal("0.06"),
        BigDecimal("20"),
        BigDecimal("0.02")
      ),
      PortfolioDailySummary(
        LocalDate.of(2024, 7, 1),
        BigDecimal("100"),
        BigDecimal("0.05"),
        BigDecimal("10"),
        BigDecimal("0.01")
      )
    )
    whenever(portfolioDailySummaryRepository.findAll(any<PageRequest>())).thenReturn(PageImpl(summaries))

    val result = portfolioSummaryService.getAllDailySummaries(0, 10)

    assertThat(result.content)
      .hasSize(2)
      .isEqualTo(summaries)
  }

  @Test
  fun `recalculateAllDailySummaries should handle empty transactions`() {
    whenever(portfolioTransactionService.getAllTransactions()).thenReturn(emptyList())

    val count = portfolioSummaryService.recalculateAllDailySummaries()

    assertThat(count)
      .isZero()
    verify(portfolioDailySummaryRepository, never()).deleteAll()
    verify(portfolioDailySummaryRepository, never()).saveAll(any<List<PortfolioDailySummary>>())
  }

  @Test
  fun `recalculateAllDailySummaries should process all dates between first transaction and yesterday`() {
    // Fix: Set up a specific test date range
    val today = LocalDate.of(2024, 7, 5)
    val instant = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
    whenever(clock.instant()).thenReturn(instant) // Override the default instant
    whenever(clock.zone).thenReturn(ZoneId.systemDefault())

    val transaction = PortfolioTransaction(
      instrument = instrument,
      transactionType = TransactionType.BUY,
      quantity = BigDecimal("10"),
      price = BigDecimal("100"),
      transactionDate = LocalDate.of(2024, 7, 1), // First transaction on July 1
      platform = this.transaction.platform
    )

    whenever(portfolioTransactionService.getAllTransactions()).thenReturn(listOf(transaction))
    whenever(dailyPriceService.getPrice(any(), any())).thenReturn(BigDecimal("110"))

    // Make sure unified profit calculation doesn't throw for test
    whenever(unifiedProfitCalculationService.calculateCurrentHoldings(any()))
      .thenReturn(transaction.quantity to transaction.price)

    // Make sure xirr calculation works with all date params
    whenever(unifiedProfitCalculationService.calculateAdjustedXirr(any(), any(), any())).thenReturn(0.05)

    // Return whatever is passed to saveAll
    whenever(portfolioDailySummaryRepository.saveAll(any<List<PortfolioDailySummary>>()))
      .thenAnswer { invocation -> invocation.arguments[0] as List<*> }

    // Clear the existing summaries
    val emptyList = emptyList<PortfolioDailySummary>()
    whenever(portfolioDailySummaryRepository.findAll()).thenReturn(emptyList)

    val count = portfolioSummaryService.recalculateAllDailySummaries()

    // Should be 4 dates processed (July 1, 2, 3, 4) but not today (July 5)
    assertThat(count).isEqualTo(4)

    // Verify we deleted existing summaries and saved new ones
    verify(portfolioDailySummaryRepository).findAll()
    verify(portfolioDailySummaryRepository).flush()
    verify(portfolioDailySummaryRepository).saveAll(summaryListCaptor.capture())

    // Verify we processed all dates from first transaction to yesterday
    val processedDates = summaryListCaptor.value.map { it.entryDate }
    assertThat(processedDates)
      .containsExactlyInAnyOrder(
        LocalDate.of(2024, 7, 1),
        LocalDate.of(2024, 7, 2),
        LocalDate.of(2024, 7, 3),
        LocalDate.of(2024, 7, 4)
      )
      .doesNotContain(today) // Make sure today wasn't processed
  }

  @Test
  fun `saveDailySummaries should update existing summaries and add new ones`() {
    val existingDate = LocalDate.of(2024, 7, 1)
    val existing =
      PortfolioDailySummary(existingDate, BigDecimal("100"), BigDecimal("0.05"), BigDecimal("10"), BigDecimal("0.01"))
    val updatedSummary =
      PortfolioDailySummary(existingDate, BigDecimal("200"), BigDecimal("0.06"), BigDecimal("20"), BigDecimal("0.02"))
    val newDate = LocalDate.of(2024, 7, 2)
    val newSummary =
      PortfolioDailySummary(newDate, BigDecimal("300"), BigDecimal("0.07"), BigDecimal("30"), BigDecimal("0.03"))

    whenever(portfolioDailySummaryRepository.findAllByEntryDateIn(listOf(existingDate, newDate))).thenReturn(
      listOf(
        existing
      )
    )
    whenever(portfolioDailySummaryRepository.saveAll(any<List<PortfolioDailySummary>>())).thenAnswer { invocation -> invocation.arguments[0] as List<PortfolioDailySummary> }

    portfolioSummaryService.saveDailySummaries(listOf(updatedSummary, newSummary))

    verify(portfolioDailySummaryRepository).saveAll(summaryListCaptor.capture())
    val saved = summaryListCaptor.value
    assertThat(saved)
      .hasSize(2)
    val firstSaved = saved.first { it.entryDate == existingDate }
    assertThat(firstSaved.totalValue)
      .isEqualByComparingTo(BigDecimal("200"))
    assertThat(firstSaved.xirrAnnualReturn)
      .isEqualByComparingTo(BigDecimal("0.06"))
    assertThat(firstSaved.totalProfit)
      .isEqualByComparingTo(BigDecimal("20"))
    assertThat(firstSaved.earningsPerDay)
      .isEqualByComparingTo(BigDecimal("0.02"))
    assertThat(saved.first { it.entryDate == newDate })
      .isEqualTo(newSummary)
  }

  @Test
  fun `getDailySummariesBetween should return summaries between dates`() {
    val start = LocalDate.of(2024, 7, 1)
    val end = LocalDate.of(2024, 7, 5)
    val between = listOf(
      PortfolioDailySummary(
        LocalDate.of(2024, 7, 2),
        BigDecimal("110"),
        BigDecimal("0.06"),
        BigDecimal("20"),
        BigDecimal("0.02")
      ),
      PortfolioDailySummary(
        LocalDate.of(2024, 7, 3),
        BigDecimal("120"),
        BigDecimal("0.07"),
        BigDecimal("30"),
        BigDecimal("0.03")
      )
    )
    whenever(portfolioDailySummaryRepository.findAllByEntryDateBetween(start, end)).thenReturn(between)

    val result = portfolioSummaryService.getDailySummariesBetween(start, end)

    assertThat(result)
      .hasSize(2)
      .isEqualTo(between)
  }
}
