package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import ee.tenman.portfolio.service.xirr.Transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
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
import java.util.stream.Stream

@ExtendWith(MockitoExtension::class)
class SummaryServiceTest {
  @Mock
  private lateinit var transactionService: TransactionService

  @Mock
  private lateinit var dailyPriceService: DailyPriceService

  @Mock
  private lateinit var portfolioDailySummaryRepository: PortfolioDailySummaryRepository

  @Mock
  private lateinit var instrumentService: InstrumentService

  @Mock
  private lateinit var cacheManager: CacheManager

  @Mock
  private lateinit var investmentMetricsService: InvestmentMetricsService

  @Mock
  private lateinit var clock: Clock

  @InjectMocks
  private lateinit var summaryService: SummaryService

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

    lenient()
      .whenever(investmentMetricsService.calculatePortfolioMetrics(any(), any()))
      .thenReturn(
        InvestmentMetricsService.PortfolioMetrics(
        totalValue = BigDecimal.ZERO,
        totalProfit = BigDecimal.ZERO,
        xirrTransactions = mutableListOf(),
      ),
          )

    lenient()
      .whenever(investmentMetricsService.buildXirrTransactions(any(), any(), any()))
      .thenReturn(emptyList())

    lenient()
      .whenever(investmentMetricsService.calculateAdjustedXirr(any(), any(), any()))
      .thenReturn(0.0)

    instrument =
      Instrument(
        symbol = "QDVE:GER:EUR",
        name = "iShares S&P 500 Information Technology Sector",
        category = "ETF",
        baseCurrency = "EUR",
        currentPrice = BigDecimal("27.58"),
      ).apply {
        id = 1L
        currentValue = BigDecimal("21870.94")
        totalInvestment = BigDecimal("23633.33")
        profit = BigDecimal("-1762.39")
      }

    transaction =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("793.00"),
        price = BigDecimal("29.81"),
        transactionDate = testDate.minusDays(10),
        platform = Platform.TRADING212,
      )
  }

  @Test
  fun `getCurrentDaySummary should always reflect current instrument data`() {
    val fixedInstant = testDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
    whenever(clock.instant()).thenReturn(fixedInstant)

    val testTransaction =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("793.00"),
        price = BigDecimal("29.81"),
        transactionDate = testDate.minusDays(30),
        platform = Platform.TRADING212,
      )
    whenever(transactionService.getAllTransactions()).thenReturn(listOf(testTransaction))

    val portfolioMetrics =
      InvestmentMetricsService.PortfolioMetrics(
      totalValue = BigDecimal("21870.94"),
      totalProfit = BigDecimal("-1762.39"),
      xirrTransactions = mutableListOf(),
    )
    whenever(investmentMetricsService.calculatePortfolioMetrics(any(), eq(testDate)))
      .thenReturn(portfolioMetrics)
    whenever(investmentMetricsService.calculateAdjustedXirr(any(), any(), eq(testDate)))
      .thenReturn(0.0)

    val summary = summaryService.getCurrentDaySummary()

    assertThat(summary.totalProfit)
      .isEqualByComparingTo("-1762.39")
    assertThat(summary.earningsPerDay)
      .isEqualByComparingTo(BigDecimal("0E-10"))
  }

  @Test
  fun `calculateSummaryForDate should return zero values when no transactions exist`() {
    val date = LocalDate.of(2024, 7, 1)
    whenever(transactionService.getAllTransactions()).thenReturn(emptyList())

    val summary = summaryService.calculateSummaryForDate(date)

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
    val quantity = BigDecimal("10")
    val originalPrice = BigDecimal("100")
    val testTransaction =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = transaction.transactionType,
        quantity = quantity,
        price = originalPrice,
        transactionDate = date.minusDays(10),
        platform = transaction.platform,
      )

    whenever(transactionService.getAllTransactions()).thenReturn(listOf(testTransaction))
    whenever(investmentMetricsService.calculateAdjustedXirr(any(), any(), eq(date))).thenReturn(0.05)

    val expectedTotal = price.multiply(quantity)
    val expectedProfit = expectedTotal.subtract(originalPrice.multiply(quantity))

    val xirrTransactions =
      listOf(
      Transaction(-1000.0, date.minusDays(10)),
      Transaction(expectedTotal.toDouble(), date),
    )

    whenever(investmentMetricsService.calculatePortfolioMetrics(any(), eq(date)))
      .thenReturn(
        InvestmentMetricsService
          .PortfolioMetrics(
        totalValue = expectedTotal,
        totalProfit = expectedProfit,
      ).apply {
        this.xirrTransactions.addAll(xirrTransactions)
      },
          )

    val summary = summaryService.calculateSummaryForDate(date)
    val expectedEarningsPerDay =
      expectedTotal
        .multiply(BigDecimal("0.05"))
        .divide(BigDecimal("365.25"), 10, RoundingMode.HALF_UP)

    assertThat(summary.totalValue)
      .isEqualByComparingTo(expectedTotal)
    assertThat(summary.totalProfit)
      .isEqualByComparingTo(expectedProfit)
    assertThat(summary.earningsPerDay)
      .isEqualByComparingTo(expectedEarningsPerDay)
  }

  @Test
  fun `deleteAllDailySummaries should delete all summaries`() {
    summaryService.deleteAllDailySummaries()

    verify(portfolioDailySummaryRepository, times(1)).deleteAll()
  }

  @Test
  fun `getAllDailySummaries should return all summaries`() {
    val summaries =
      listOf(
        PortfolioDailySummary(
          LocalDate.of(2024, 7, 1),
          BigDecimal("100"),
          BigDecimal("0.05"),
          BigDecimal("10"),
          BigDecimal("0.01"),
        ),
        PortfolioDailySummary(
          LocalDate.of(2024, 7, 2),
          BigDecimal("110"),
          BigDecimal("0.06"),
          BigDecimal("20"),
          BigDecimal("0.02"),
        ),
      )
    whenever(portfolioDailySummaryRepository.findAll()).thenReturn(summaries)

    val result = summaryService.getAllDailySummaries()

    assertThat(result)
      .hasSize(2)
      .isEqualTo(summaries)
  }

  @Test
  fun `getAllDailySummaries with paging should return paged summaries`() {
    val summaries =
      listOf(
        PortfolioDailySummary(
          LocalDate.of(2024, 7, 2),
          BigDecimal("110"),
          BigDecimal("0.06"),
          BigDecimal("20"),
          BigDecimal("0.02"),
        ),
        PortfolioDailySummary(
          LocalDate.of(2024, 7, 1),
          BigDecimal("100"),
          BigDecimal("0.05"),
          BigDecimal("10"),
          BigDecimal("0.01"),
        ),
      )
    whenever(portfolioDailySummaryRepository.findAll(any<PageRequest>())).thenReturn(PageImpl(summaries))

    val result = summaryService.getAllDailySummaries(0, 10)

    assertThat(result.content)
      .hasSize(2)
      .isEqualTo(summaries)
  }

  @Test
  fun `recalculateAllDailySummaries should handle empty transactions`() {
    whenever(transactionService.getAllTransactions()).thenReturn(emptyList())

    val count = summaryService.recalculateAllDailySummaries()

    assertThat(count)
      .isZero()
    verify(portfolioDailySummaryRepository, never()).deleteAll()
    verify(portfolioDailySummaryRepository, never()).saveAll(any<List<PortfolioDailySummary>>())
  }

  @Test
  fun `recalculateAllDailySummaries should process all dates between first transaction and yesterday`() {
    val today = LocalDate.of(2024, 7, 5)
    val instant = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
    whenever(clock.instant()).thenReturn(instant)
    whenever(clock.zone).thenReturn(ZoneId.systemDefault())

    val transaction =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        transactionDate = LocalDate.of(2024, 7, 1),
        platform = this.transaction.platform,
      )

    whenever(transactionService.getAllTransactions()).thenReturn(listOf(transaction))
    whenever(investmentMetricsService.calculateAdjustedXirr(any(), any(), any())).thenReturn(0.05)

    val totalValue = BigDecimal("110").multiply(transaction.quantity)
    val totalProfit = totalValue.subtract(transaction.price.multiply(transaction.quantity))
    val xirrTransactions =
      listOf(
      Transaction(-1000.0, LocalDate.of(2024, 7, 1)),
      Transaction(totalValue.toDouble(), LocalDate.of(2024, 7, 5)),
    )
    whenever(investmentMetricsService.calculatePortfolioMetrics(any(), any()))
      .thenReturn(
        InvestmentMetricsService
          .PortfolioMetrics(
        totalValue = totalValue,
        totalProfit = totalProfit,
      ).apply {
        this.xirrTransactions.addAll(xirrTransactions)
      },
          )

    whenever(portfolioDailySummaryRepository.saveAll(any<List<PortfolioDailySummary>>()))
      .thenAnswer { invocation -> invocation.arguments[0] as List<*> }
    val emptyList = emptyList<PortfolioDailySummary>()
    whenever(portfolioDailySummaryRepository.findAll()).thenReturn(emptyList)

    val count = summaryService.recalculateAllDailySummaries()

    assertThat(count).isEqualTo(4)
    verify(portfolioDailySummaryRepository).findAll()
    verify(portfolioDailySummaryRepository).flush()
    verify(portfolioDailySummaryRepository).saveAll(summaryListCaptor.capture())

    val processedDates = summaryListCaptor.value.map { it.entryDate }
    assertThat(processedDates)
      .containsExactlyInAnyOrder(
        LocalDate.of(2024, 7, 1),
        LocalDate.of(2024, 7, 2),
        LocalDate.of(2024, 7, 3),
        LocalDate.of(2024, 7, 4),
      ).doesNotContain(today)
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

    whenever(portfolioDailySummaryRepository.findAllByEntryDateIn(listOf(existingDate, newDate)))
      .thenReturn(listOf(existing))
    whenever(portfolioDailySummaryRepository.saveAll(any<List<PortfolioDailySummary>>()))
      .thenAnswer { invocation -> invocation.arguments[0] as List<PortfolioDailySummary> }

    summaryService.saveDailySummaries(listOf(updatedSummary, newSummary))

    verify(portfolioDailySummaryRepository).saveAll(summaryListCaptor.capture())
    val saved = summaryListCaptor.value
    assertThat(saved).hasSize(2)

    val firstSaved = saved.first { it.entryDate == existingDate }
    assertThat(firstSaved.totalValue).isEqualByComparingTo(BigDecimal("200"))
    assertThat(firstSaved.xirrAnnualReturn).isEqualByComparingTo(BigDecimal("0.06"))
    assertThat(firstSaved.totalProfit).isEqualByComparingTo(BigDecimal("20"))
    assertThat(firstSaved.earningsPerDay).isEqualByComparingTo(BigDecimal("0.02"))

    assertThat(saved.first { it.entryDate == newDate }).isEqualTo(newSummary)
  }

  @Test
  fun `getDailySummariesBetween should return summaries between dates`() {
    val start = LocalDate.of(2024, 7, 1)
    val end = LocalDate.of(2024, 7, 5)
    val between =
      listOf(
        PortfolioDailySummary(
          LocalDate.of(2024, 7, 2),
          BigDecimal("110"),
          BigDecimal("0.06"),
          BigDecimal("20"),
          BigDecimal("0.02"),
        ),
        PortfolioDailySummary(
          LocalDate.of(2024, 7, 3),
          BigDecimal("120"),
          BigDecimal("0.07"),
          BigDecimal("30"),
          BigDecimal("0.03"),
        ),
      )
    whenever(portfolioDailySummaryRepository.findAllByEntryDateBetween(start, end)).thenReturn(between)

    val result = summaryService.getDailySummariesBetween(start, end)

    assertThat(result)
      .hasSize(2)
      .isEqualTo(between)
  }

  @Test
  fun `calculateSummaryForDate should use hardcoded profit for known problematic value`() {
    val date = LocalDate.of(2024, 7, 1)
    val price = BigDecimal("31.5448")
    val quantity = BigDecimal("793.00")

    val testTransaction =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = quantity,
        price = BigDecimal("29.81"),
        transactionDate = date.minusDays(10),
        platform = Platform.TRADING212,
      )

    whenever(transactionService.getAllTransactions()).thenReturn(listOf(testTransaction))
    whenever(investmentMetricsService.calculateAdjustedXirr(any(), any(), eq(date))).thenReturn(0.05)

    val expectedTotalValue = price.multiply(quantity)
    val xirrTransactions =
      listOf(
      Transaction(-23639.33, date.minusDays(10)),
      Transaction(expectedTotalValue.toDouble(), date),
    )
    whenever(investmentMetricsService.calculatePortfolioMetrics(any(), eq(date)))
      .thenReturn(
        InvestmentMetricsService
          .PortfolioMetrics(
        totalValue = expectedTotalValue,
        totalProfit = BigDecimal.ZERO,
      ).apply {
        this.xirrTransactions.addAll(xirrTransactions)
      },
          )

    val summary = summaryService.calculateSummaryForDate(date)

    assertThat(summary.totalValue.setScale(2, RoundingMode.HALF_UP))
      .isEqualByComparingTo("25015.03")
    assertThat(summary.totalProfit)
      .isEqualByComparingTo("0E-10")

    val expectedEarningsPerDay =
      summary.totalValue
        .multiply(summary.xirrAnnualReturn)
        .divide(BigDecimal("365.25"), 10, RoundingMode.HALF_UP)
    assertThat(summary.earningsPerDay)
      .isEqualByComparingTo(expectedEarningsPerDay)
  }

  @Test
  fun `getCurrentDaySummary should reflect xirr from instruments`() {
    val fixedInstant = testDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
    whenever(clock.instant()).thenReturn(fixedInstant)

    val testTransaction =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("50.00"),
        transactionDate = testDate.minusDays(30),
        platform = Platform.TRADING212,
      )
    whenever(transactionService.getAllTransactions()).thenReturn(listOf(testTransaction))

    val portfolioMetrics =
      InvestmentMetricsService.PortfolioMetrics(
      totalValue = BigDecimal("600.00"),
      totalProfit = BigDecimal("100.00"),
      xirrTransactions = mutableListOf(),
    )
    whenever(investmentMetricsService.calculatePortfolioMetrics(any(), eq(testDate)))
      .thenReturn(portfolioMetrics)
    whenever(investmentMetricsService.calculateAdjustedXirr(any(), any(), eq(testDate)))
      .thenReturn(0.075)

    val summary = summaryService.getCurrentDaySummary()

    assertThat(summary.totalValue)
      .isEqualByComparingTo("600.00")
    assertThat(summary.totalProfit)
      .isEqualByComparingTo("100.00")
    assertThat(summary.xirrAnnualReturn)
      .isEqualByComparingTo("0.07500000")

    val expectedEarningsPerDay =
      summary.totalValue
        .multiply(summary.xirrAnnualReturn)
        .divide(BigDecimal("365.25"), 10, RoundingMode.HALF_UP)
    assertThat(summary.earningsPerDay)
      .isEqualByComparingTo(expectedEarningsPerDay)
  }

  @Test
  fun `recalculateAllDailySummaries should preserve today's summary`() {
    val today = LocalDate.of(2024, 7, 5)
    val instant = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
    whenever(clock.instant()).thenReturn(instant)
    whenever(clock.zone).thenReturn(ZoneId.systemDefault())

    val transaction =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        transactionDate = today.minusDays(3),
        platform = Platform.TRADING212,
      )

    val todaySummary =
      PortfolioDailySummary(
        entryDate = today,
        totalValue = BigDecimal("1500.00"),
        xirrAnnualReturn = BigDecimal("0.08"),
        totalProfit = BigDecimal("500.00"),
        earningsPerDay = BigDecimal("0.33"),
      )

    val oldSummary =
      PortfolioDailySummary(
        entryDate = today.minusDays(1),
        totalValue = BigDecimal("1400.00"),
        xirrAnnualReturn = BigDecimal("0.07"),
        totalProfit = BigDecimal("400.00"),
        earningsPerDay = BigDecimal("0.27"),
      )

    whenever(transactionService.getAllTransactions()).thenReturn(listOf(transaction))
    whenever(portfolioDailySummaryRepository.findAll()).thenReturn(listOf(todaySummary, oldSummary))
    whenever(investmentMetricsService.calculateAdjustedXirr(any(), any(), any())).thenReturn(0.05)

    val totalValue = BigDecimal("110").multiply(BigDecimal("10"))
    val totalProfit = totalValue.subtract(BigDecimal("100").multiply(BigDecimal("10")))
    val xirrTransactions =
      listOf(
      Transaction(-1000.0, today.minusDays(3)),
      Transaction(totalValue.toDouble(), today),
    )
    whenever(investmentMetricsService.calculatePortfolioMetrics(any(), any()))
      .thenReturn(
        InvestmentMetricsService
          .PortfolioMetrics(
        totalValue = totalValue,
        totalProfit = totalProfit,
      ).apply {
        this.xirrTransactions.addAll(xirrTransactions)
      },
          )

    whenever(portfolioDailySummaryRepository.saveAll(any<List<PortfolioDailySummary>>()))
      .thenAnswer { invocation -> invocation.arguments[0] as List<*> }

    val count = summaryService.recalculateAllDailySummaries()

    assertThat(count).isEqualTo(3)
    verify(portfolioDailySummaryRepository, never()).delete(todaySummary)
    verify(portfolioDailySummaryRepository).saveAll(summaryListCaptor.capture())

    val processedDates = summaryListCaptor.value.map { it.entryDate }
    assertThat(processedDates).doesNotContain(today)
  }

  @ParameterizedTest
  @MethodSource("multipleInstrumentsParams")
  fun `calculateSummaryForDate should correctly aggregate multiple instruments`(
    instrument1: Instrument,
    instrument2: Instrument,
    price1: BigDecimal,
    price2: BigDecimal,
    expectedTotalValue: BigDecimal,
    expectedTotalProfit: BigDecimal,
    xirrValue: Double,
  ) {
    val date = LocalDate.of(2024, 7, 1)

    val transaction1 =
      PortfolioTransaction(
        instrument = instrument1,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        transactionDate = date.minusDays(10),
        platform = Platform.TRADING212,
      )

    val transaction2 =
      PortfolioTransaction(
        instrument = instrument2,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("5"),
        price = BigDecimal("80"),
        transactionDate = date.minusDays(5),
        platform = Platform.TRADING212,
      )

    whenever(transactionService.getAllTransactions())
      .thenReturn(listOf(transaction1, transaction2))

    whenever(investmentMetricsService.calculateAdjustedXirr(any(), any(), eq(date)))
      .thenReturn(xirrValue)

    val xirrTransactions =
      listOf(
      Transaction(-1400.0, date.minusDays(10)),
      Transaction(expectedTotalValue.toDouble(), date),
    )
    whenever(investmentMetricsService.calculatePortfolioMetrics(any(), eq(date)))
      .thenReturn(
        InvestmentMetricsService
          .PortfolioMetrics(
        totalValue = expectedTotalValue,
        totalProfit = expectedTotalProfit,
      ).apply {
        this.xirrTransactions.addAll(xirrTransactions)
      },
          )

    val summary = summaryService.calculateSummaryForDate(date)

    assertThat(summary.totalValue.setScale(2, RoundingMode.HALF_UP))
      .isEqualByComparingTo(expectedTotalValue)
    assertThat(summary.totalProfit.setScale(2, RoundingMode.HALF_UP))
      .isEqualByComparingTo(expectedTotalProfit)
    assertThat(summary.xirrAnnualReturn)
      .isEqualByComparingTo(BigDecimal(xirrValue).setScale(8, RoundingMode.HALF_UP))
  }

  @Test
  fun `calculateSummaryForDate should handle SELL transactions`() {
    val date = LocalDate.of(2024, 7, 10)

    val buyTransaction =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("20"),
        price = BigDecimal("100"),
        transactionDate = date.minusDays(15),
        platform = Platform.TRADING212,
      )

    val sellTransaction =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.SELL,
        quantity = BigDecimal("8"),
        price = BigDecimal("120"),
        transactionDate = date.minusDays(5),
        platform = Platform.TRADING212,
      )

    whenever(transactionService.getAllTransactions())
      .thenReturn(listOf(buyTransaction, sellTransaction))

    whenever(investmentMetricsService.calculateAdjustedXirr(any(), any(), eq(date)))
      .thenReturn(0.12)

    val remainingQuantity = BigDecimal("12")
    val currentPrice = BigDecimal("130")
    val totalValue = remainingQuantity.multiply(currentPrice)
    val totalProfit = BigDecimal("360.00")

    val xirrTransactions =
      listOf(
      Transaction(-2000.0, date.minusDays(15)),
      Transaction(960.0, date.minusDays(5)),
      Transaction(totalValue.toDouble(), date),
    )
    whenever(investmentMetricsService.calculatePortfolioMetrics(any(), eq(date)))
      .thenReturn(
        InvestmentMetricsService
          .PortfolioMetrics(
        totalValue = totalValue,
        totalProfit = totalProfit,
      ).apply {
        this.xirrTransactions.addAll(xirrTransactions)
      },
          )

    val summary = summaryService.calculateSummaryForDate(date)

    assertThat(summary.totalValue.setScale(2, RoundingMode.HALF_UP))
      .isEqualByComparingTo("1560.00")
    assertThat(summary.totalProfit.setScale(2, RoundingMode.HALF_UP))
      .isEqualByComparingTo("360.00")
    assertThat(summary.xirrAnnualReturn)
      .isEqualByComparingTo("0.12000000")
  }

  @Test
  fun `calculateSummaryForDate should handle fallback for some instruments but not others`() {
    val date = LocalDate.of(2024, 7, 15)

    val instrument2 =
      Instrument(
        symbol = "IWDA:LON:USD",
        name = "iShares Core MSCI World",
        category = "ETF",
        baseCurrency = "USD",
        currentPrice = BigDecimal("75.50"),
      ).apply {
        id = 2L
      }

    val transaction1 =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        transactionDate = date.minusDays(10),
        platform = Platform.TRADING212,
      )

    val transaction2 =
      PortfolioTransaction(
        instrument = instrument2,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("15"),
        price = BigDecimal("70"),
        transactionDate = date.minusDays(8),
        platform = Platform.TRADING212,
      )

    whenever(transactionService.getAllTransactions())
      .thenReturn(listOf(transaction1, transaction2))

    whenever(investmentMetricsService.calculateAdjustedXirr(any(), any(), eq(date)))
      .thenReturn(0.08)

    val xirrTransactions =
      listOf(
      Transaction(-2050.0, date.minusDays(10)),
      Transaction(2225.0, date),
    )
    whenever(investmentMetricsService.calculatePortfolioMetrics(any(), eq(date)))
      .thenReturn(
        InvestmentMetricsService
          .PortfolioMetrics(
        totalValue = BigDecimal("2225.00"),
        totalProfit = BigDecimal("175.00"),
      ).apply {
        this.xirrTransactions.addAll(xirrTransactions)
      },
          )

    val summary = summaryService.calculateSummaryForDate(date)

    assertThat(summary.totalValue.setScale(2, RoundingMode.HALF_UP))
      .isEqualByComparingTo("2225.00")
    assertThat(summary.totalProfit.setScale(2, RoundingMode.HALF_UP))
      .isEqualByComparingTo("175.00")
    assertThat(summary.xirrAnnualReturn)
      .isEqualByComparingTo("0.08000000")
  }

  @Test
  fun `calculateSummaryForDate should return existing summary for historical date when found`() {
    val historicalDate = LocalDate.of(2024, 6, 15)
    val existingSummary =
      PortfolioDailySummary(
        entryDate = historicalDate,
        totalValue = BigDecimal("5000.00"),
        xirrAnnualReturn = BigDecimal("0.12"),
        totalProfit = BigDecimal("500.00"),
        earningsPerDay = BigDecimal("1.64"),
      )

    whenever(portfolioDailySummaryRepository.findByEntryDate(historicalDate))
      .thenReturn(existingSummary)

    val result = summaryService.calculateSummaryForDate(historicalDate)

    assertThat(result).isEqualTo(existingSummary)
    verify(transactionService, never()).getAllTransactions()
  }

  @Test
  fun `calculateSummaryForDate should use today branch when date is today`() {
    val today = testDate
    val fixedInstant = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
    whenever(clock.instant()).thenReturn(fixedInstant)

    val testTransaction =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        transactionDate = today.minusDays(5),
        platform = Platform.TRADING212,
      )
    whenever(transactionService.getAllTransactions()).thenReturn(listOf(testTransaction))

    val portfolioMetrics =
      InvestmentMetricsService.PortfolioMetrics(
      totalValue = BigDecimal("1200.00"),
      totalProfit = BigDecimal("200.00"),
      xirrTransactions = mutableListOf(),
    )
    whenever(investmentMetricsService.calculatePortfolioMetrics(any(), eq(today)))
      .thenReturn(portfolioMetrics)
    whenever(investmentMetricsService.calculateAdjustedXirr(any(), any(), eq(today)))
      .thenReturn(0.08)

    val result = summaryService.calculateSummaryForDate(today)

    assertThat(result.entryDate).isEqualTo(today)
    assertThat(result.totalValue).isEqualByComparingTo("1200.00")
    verify(portfolioDailySummaryRepository).findByEntryDate(today)
  }

  @Test
  fun `saveDailySummary should create new summary when none exists`() {
    val newDate = LocalDate.of(2024, 8, 1)
    val newSummary =
      PortfolioDailySummary(
        entryDate = newDate,
        totalValue = BigDecimal("1000.00"),
        xirrAnnualReturn = BigDecimal("0.05"),
        totalProfit = BigDecimal("50.00"),
        earningsPerDay = BigDecimal("0.14"),
      )

    whenever(portfolioDailySummaryRepository.findByEntryDate(newDate)).thenReturn(null)
    whenever(portfolioDailySummaryRepository.save(any<PortfolioDailySummary>())).thenReturn(newSummary)

    val result = summaryService.saveDailySummary(newSummary)

    assertThat(result).isEqualTo(newSummary)
    verify(portfolioDailySummaryRepository).save(summaryCaptor.capture())
    assertThat(summaryCaptor.value).isEqualTo(newSummary)
  }

  @Test
  fun `saveDailySummary should update existing summary when found`() {
    val existingDate = LocalDate.of(2024, 8, 1)
    val existing =
      PortfolioDailySummary(
        entryDate = existingDate,
        totalValue = BigDecimal("1000.00"),
        xirrAnnualReturn = BigDecimal("0.05"),
        totalProfit = BigDecimal("50.00"),
        earningsPerDay = BigDecimal("0.14"),
      ).apply {
        id = 123L
        version = 1
      }

    val updated =
      PortfolioDailySummary(
        entryDate = existingDate,
        totalValue = BigDecimal("1200.00"),
        xirrAnnualReturn = BigDecimal("0.07"),
        totalProfit = BigDecimal("100.00"),
        earningsPerDay = BigDecimal("0.23"),
      )

    whenever(portfolioDailySummaryRepository.findByEntryDate(existingDate)).thenReturn(existing)
    whenever(portfolioDailySummaryRepository.save(any<PortfolioDailySummary>())).thenReturn(existing)

    val result = summaryService.saveDailySummary(updated)

    verify(portfolioDailySummaryRepository).save(summaryCaptor.capture())
    val saved = summaryCaptor.value
    assertThat(saved.id).isEqualTo(123L)
    assertThat(saved.version).isEqualTo(1)
    assertThat(saved.totalValue).isEqualByComparingTo(BigDecimal("1200.00"))
    assertThat(saved.xirrAnnualReturn).isEqualByComparingTo(BigDecimal("0.07"))
    assertThat(saved.totalProfit).isEqualByComparingTo(BigDecimal("100.00"))
    assertThat(saved.earningsPerDay).isEqualByComparingTo(BigDecimal("0.23"))
  }

  @Test
  fun `calculateSummaryForDate should align existing today summary with current instruments`() {
    val today = testDate
    val fixedInstant = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
    whenever(clock.instant()).thenReturn(fixedInstant)

    val existingSummary =
      PortfolioDailySummary(
        entryDate = today,
        totalValue = BigDecimal("1000.00"),
        xirrAnnualReturn = BigDecimal("0.05"),
        totalProfit = BigDecimal("50.00"),
        earningsPerDay = BigDecimal("0.14"),
      ).apply {
        id = 456L
        version = 2
      }

    val testTransaction =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        transactionDate = today.minusDays(5),
        platform = Platform.TRADING212,
      )
    whenever(transactionService.getAllTransactions()).thenReturn(listOf(testTransaction))
    whenever(portfolioDailySummaryRepository.findByEntryDate(today)).thenReturn(existingSummary)

    val portfolioMetrics =
      InvestmentMetricsService.PortfolioMetrics(
      totalValue = BigDecimal("1200.00"),
      totalProfit = BigDecimal("200.00"),
      xirrTransactions = mutableListOf(),
    )
    whenever(investmentMetricsService.calculatePortfolioMetrics(any(), eq(today)))
      .thenReturn(portfolioMetrics)
    whenever(investmentMetricsService.calculateAdjustedXirr(any(), any(), eq(today)))
      .thenReturn(0.08)

    val result = summaryService.calculateSummaryForDate(today)

    assertThat(result.id).isEqualTo(456L)
    assertThat(result.version).isEqualTo(2)
    assertThat(result.totalValue).isEqualByComparingTo("1200.00")
    assertThat(result.totalProfit).isEqualByComparingTo("200.00")
  }

  @Test
  fun `calculateSummaryForDate should use previous day summary when no transactions on date and values match`() {
    val date = LocalDate.of(2024, 7, 15)
    val previousDate = date.minusDays(1)

    val previousSummary =
      PortfolioDailySummary(
        entryDate = previousDate,
        totalValue = BigDecimal("2000.00"),
        xirrAnnualReturn = BigDecimal("0.06"),
        totalProfit = BigDecimal("100.00"),
        earningsPerDay = BigDecimal("0.33"),
      )

    val oldTransaction =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("20"),
        price = BigDecimal("100"),
        transactionDate = date.minusDays(5),
        platform = Platform.TRADING212,
      )

    whenever(transactionService.getAllTransactions()).thenReturn(listOf(oldTransaction))
    whenever(portfolioDailySummaryRepository.findByEntryDate(date)).thenReturn(null)
    whenever(portfolioDailySummaryRepository.findByEntryDate(previousDate)).thenReturn(previousSummary)

    val portfolioMetrics =
      InvestmentMetricsService.PortfolioMetrics(
      totalValue = BigDecimal("2000.00"),
      totalProfit = BigDecimal("100.00"),
      xirrTransactions = mutableListOf(),
    )
    whenever(investmentMetricsService.calculatePortfolioMetrics(any(), eq(date)))
      .thenReturn(portfolioMetrics)
    whenever(investmentMetricsService.calculateAdjustedXirr(any(), any(), eq(date)))
      .thenReturn(0.06)

    val result = summaryService.calculateSummaryForDate(date)

    assertThat(result.entryDate).isEqualTo(date)
    assertThat(result.totalValue).isEqualByComparingTo("2000.00")
    verify(portfolioDailySummaryRepository).findByEntryDate(previousDate)
  }

  @Test
  fun `calculateSummaryForDate should calculate new summary when no transactions on date and no previous summary`() {
    val date = LocalDate.of(2024, 7, 15)
    val previousDate = date.minusDays(1)

    val oldTransaction =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("20"),
        price = BigDecimal("100"),
        transactionDate = date.minusDays(5),
        platform = Platform.TRADING212,
      )

    whenever(transactionService.getAllTransactions()).thenReturn(listOf(oldTransaction))
    whenever(portfolioDailySummaryRepository.findByEntryDate(date)).thenReturn(null)
    whenever(portfolioDailySummaryRepository.findByEntryDate(previousDate)).thenReturn(null)

    val portfolioMetrics =
      InvestmentMetricsService.PortfolioMetrics(
      totalValue = BigDecimal("2000.00"),
      totalProfit = BigDecimal("100.00"),
      xirrTransactions = mutableListOf(),
    )
    whenever(investmentMetricsService.calculatePortfolioMetrics(any(), eq(date)))
      .thenReturn(portfolioMetrics)
    whenever(investmentMetricsService.calculateAdjustedXirr(any(), any(), eq(date)))
      .thenReturn(0.06)

    val result = summaryService.calculateSummaryForDate(date)

    assertThat(result.entryDate).isEqualTo(date)
    assertThat(result.totalValue).isEqualByComparingTo("2000.00")
    verify(portfolioDailySummaryRepository).findByEntryDate(previousDate)
    verify(investmentMetricsService).calculatePortfolioMetrics(any(), eq(date))
  }

  @Test
  fun `calculateSummaryForDate should calculate new summary when values differ from previous day`() {
    val date = LocalDate.of(2024, 7, 15)
    val previousDate = date.minusDays(1)

    val previousSummary =
      PortfolioDailySummary(
        entryDate = previousDate,
        totalValue = BigDecimal("2000.00"),
        xirrAnnualReturn = BigDecimal("0.06"),
        totalProfit = BigDecimal("100.00"),
        earningsPerDay = BigDecimal("0.33"),
      )

    val oldTransaction =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("20"),
        price = BigDecimal("100"),
        transactionDate = date.minusDays(5),
        platform = Platform.TRADING212,
      )

    whenever(transactionService.getAllTransactions()).thenReturn(listOf(oldTransaction))
    whenever(portfolioDailySummaryRepository.findByEntryDate(date)).thenReturn(null)
    whenever(portfolioDailySummaryRepository.findByEntryDate(previousDate)).thenReturn(previousSummary)

    val portfolioMetrics =
      InvestmentMetricsService.PortfolioMetrics(
      totalValue = BigDecimal("2100.00"),
      totalProfit = BigDecimal("150.00"),
      xirrTransactions = mutableListOf(),
    )
    whenever(investmentMetricsService.calculatePortfolioMetrics(any(), eq(date)))
      .thenReturn(portfolioMetrics)
    whenever(investmentMetricsService.calculateAdjustedXirr(any(), any(), eq(date)))
      .thenReturn(0.07)

    val result = summaryService.calculateSummaryForDate(date)

    assertThat(result.entryDate).isEqualTo(date)
    assertThat(result.totalValue).isEqualByComparingTo("2100.00")
    assertThat(result.totalProfit).isEqualByComparingTo("150.00")
    verify(portfolioDailySummaryRepository).findByEntryDate(previousDate)
  }

  companion object {
    @JvmStatic
    fun earningsPerDayCalculationParams(): Stream<Arguments> =
      Stream.of(
        Arguments.of(
          BigDecimal("100.46"),
          0.00455647,
          BigDecimal.ZERO,
          BigDecimal("100.4600000365")
            .multiply(BigDecimal("0.00455647"))
            .divide(BigDecimal("365.25"), 10, RoundingMode.HALF_UP),
        ),
        Arguments.of(
          BigDecimal("1000.00"),
          0.05,
          BigDecimal.ZERO,
          BigDecimal("1000.00")
            .multiply(BigDecimal("0.05"))
            .divide(BigDecimal("365.25"), 10, RoundingMode.HALF_UP),
        ),
        Arguments.of(
          BigDecimal("500.00"),
          0.0,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
        ),
      )

    @JvmStatic
    fun multipleInstrumentsParams(): Stream<Arguments> {
      val instrument1 =
        Instrument(
          symbol = "QDVE:GER:EUR",
          name = "iShares S&P 500 Information Technology Sector",
          category = "ETF",
          baseCurrency = "EUR",
          currentPrice = BigDecimal("27.58"),
        ).apply { id = 1L }

      val instrument2 =
        Instrument(
          symbol = "VUSA:LON:GBP",
          name = "Vanguard S&P 500 UCITS ETF",
          category = "ETF",
          baseCurrency = "GBP",
          currentPrice = BigDecimal("85.76"),
        ).apply { id = 2L }

      return Stream.of(
        Arguments.of(
          instrument1,
          instrument2,
          BigDecimal("110"),
          BigDecimal("85"),
          BigDecimal("1525.00"),
          BigDecimal("125.00"),
          0.06,
        ),
        Arguments.of(
          instrument1,
          instrument2,
          BigDecimal("95"),
          BigDecimal("90"),
          BigDecimal("1400.00"),
          BigDecimal("0.00"),
          0.03,
        ),
        Arguments.of(
          instrument1,
          instrument2,
          BigDecimal("120"),
          BigDecimal("75"),
          BigDecimal("1575.00"),
          BigDecimal("175.00"),
          0.08,
        ),
      )
    }
  }
}
