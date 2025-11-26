package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import ee.tenman.portfolio.service.xirr.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
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

class SummaryServiceTest {
  private val transactionService = mockk<TransactionService>()
  private val portfolioDailySummaryRepository = mockk<PortfolioDailySummaryRepository>(relaxUnitFun = true)
  private val cacheManager = mockk<CacheManager>(relaxed = true)
  private val investmentMetricsService = mockk<InvestmentMetricsService>()
  private val clock = mockk<Clock>()
  private val summaryBatchProcessor = mockk<SummaryBatchProcessorService>(relaxed = true)
  private val summaryDeletionService = mockk<SummaryDeletionService>(relaxed = true)

  private lateinit var summaryService: SummaryService

  private val summaryCaptor = slot<PortfolioDailySummary>()
  private val summaryListCaptor = slot<List<PortfolioDailySummary>>()

  private lateinit var testDate: LocalDate
  private lateinit var instrument: Instrument
  private lateinit var transaction: PortfolioTransaction

  @BeforeEach
  fun setup() {
    summaryService =
      SummaryService(
        portfolioDailySummaryRepository,
        transactionService,
        cacheManager,
        investmentMetricsService,
        clock,
        summaryBatchProcessor,
        summaryDeletionService,
      )

    testDate = LocalDate.of(2025, 5, 10)

    val fixedInstant = ZonedDateTime.of(2025, 5, 10, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant()
    every { clock.instant() } returns fixedInstant
    every { clock.zone } returns ZoneId.systemDefault()

    every { portfolioDailySummaryRepository.findByEntryDate(any()) } returns null

    every { investmentMetricsService.calculatePortfolioMetrics(any(), any()) } returns
      PortfolioMetrics(
        totalValue = BigDecimal.ZERO,
        totalProfit = BigDecimal.ZERO,
        xirrTransactions = mutableListOf(),
      )

    every { investmentMetricsService.buildXirrTransactions(any(), any(), any()) } returns emptyList()

    every { investmentMetricsService.calculateAdjustedXirr(any(), any()) } returns 0.0

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
  fun `should getCurrentDaySummary should always reflect current instrument data`() {
    val fixedInstant = testDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
    every { clock.instant() } returns fixedInstant

    val testTransaction =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("793.00"),
        price = BigDecimal("29.81"),
        transactionDate = testDate.minusDays(30),
        platform = Platform.TRADING212,
      )
    every { transactionService.getAllTransactions() } returns listOf(testTransaction)

    val portfolioMetrics =
      PortfolioMetrics(
        totalValue = BigDecimal("21870.94"),
        totalProfit = BigDecimal("-1762.39"),
        xirrTransactions = mutableListOf(),
      )
    every { investmentMetricsService.calculatePortfolioMetrics(any(), testDate) } returns portfolioMetrics
    every { investmentMetricsService.calculateAdjustedXirr(any(), testDate) } returns 0.0

    val summary = summaryService.getCurrentDaySummary()

    expect(summary.totalProfit).toEqualNumerically(BigDecimal("-1762.39"))
    expect(summary.earningsPerDay).toEqualNumerically(BigDecimal("0E-10"))
  }

  @Test
  fun `should calculateSummaryForDate should return zero values when no transactions exist`() {
    val date = LocalDate.of(2024, 7, 1)
    every { transactionService.getAllTransactions() } returns emptyList()

    val summary = summaryService.calculateSummaryForDate(date)

    expect(summary.entryDate).toEqual(date)
    expect(summary.totalValue).toEqualNumerically(BigDecimal.ZERO)
    expect(summary.xirrAnnualReturn).toEqualNumerically(BigDecimal.ZERO)
    expect(summary.totalProfit).toEqualNumerically(BigDecimal.ZERO)
    expect(summary.earningsPerDay).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateSummaryForDate should fall back to legacy calculation when unified service fails`() {
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

    every { transactionService.getAllTransactions() } returns listOf(testTransaction)
    every { investmentMetricsService.calculateAdjustedXirr(any(), date) } returns 0.05

    val expectedTotal = price.multiply(quantity)
    val expectedProfit = expectedTotal.subtract(originalPrice.multiply(quantity))

    val xirrTransactions =
      listOf(
        Transaction(-1000.0, date.minusDays(10)),
        Transaction(expectedTotal.toDouble(), date),
      )

    every { investmentMetricsService.calculatePortfolioMetrics(any(), date) } returns
      PortfolioMetrics(
        totalValue = expectedTotal,
        totalProfit = expectedProfit,
      ).apply {
        this.xirrTransactions.addAll(xirrTransactions)
      }

    val summary = summaryService.calculateSummaryForDate(date)
    val expectedEarningsPerDay =
      expectedTotal
        .multiply(BigDecimal("0.05"))
        .divide(BigDecimal("365.25"), 10, RoundingMode.HALF_UP)

    expect(summary.totalValue).toEqualNumerically(expectedTotal)
    expect(summary.totalProfit).toEqualNumerically(expectedProfit)
    expect(summary.earningsPerDay).toEqualNumerically(expectedEarningsPerDay)
  }

  @Test
  fun `should deleteAllDailySummaries should delete all summaries`() {
    every { portfolioDailySummaryRepository.deleteAll() } returns Unit

    summaryService.deleteAllDailySummaries()

    verify(exactly = 1) { portfolioDailySummaryRepository.deleteAll() }
  }

  @Test
  fun `should getAllDailySummaries should return all summaries`() {
    val summaries =
      listOf(
        PortfolioDailySummary(
          LocalDate.of(2024, 7, 1),
          BigDecimal("100"),
          BigDecimal("0.05"),
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal("10"),
          BigDecimal("0.01"),
        ),
        PortfolioDailySummary(
          LocalDate.of(2024, 7, 2),
          BigDecimal("110"),
          BigDecimal("0.06"),
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal("20"),
          BigDecimal("0.02"),
        ),
      )
    every { portfolioDailySummaryRepository.findAll() } returns summaries

    val result = summaryService.getAllDailySummaries()

    expect(result).toHaveSize(2)
    expect(result).toEqual(summaries)
  }

  @Test
  fun `should getAllDailySummaries with paging should return paged summaries`() {
    val summaries =
      listOf(
        PortfolioDailySummary(
          LocalDate.of(2024, 7, 2),
          BigDecimal("110"),
          BigDecimal("0.06"),
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal("20"),
          BigDecimal("0.02"),
        ),
        PortfolioDailySummary(
          LocalDate.of(2024, 7, 1),
          BigDecimal("100"),
          BigDecimal("0.05"),
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal("10"),
          BigDecimal("0.01"),
        ),
      )
    every { portfolioDailySummaryRepository.findAll(any<PageRequest>()) } returns PageImpl(summaries)

    val result = summaryService.getAllDailySummaries(0, 10)

    expect(result.content).toHaveSize(2)
    expect(result.content).toEqual(summaries)
  }

  @Test
  fun `should recalculateAllDailySummaries should handle empty transactions`() {
    every { transactionService.getAllTransactions() } returns emptyList()

    val count = summaryService.recalculateAllDailySummaries()

    expect(count).toEqual(0)
    verify(exactly = 0) { portfolioDailySummaryRepository.deleteAll() }
    verify(exactly = 0) { portfolioDailySummaryRepository.saveAll(any<List<PortfolioDailySummary>>()) }
  }

  @Test
  fun `should recalculateAllDailySummaries should process all dates between first transaction and yesterday`() {
    val today = LocalDate.of(2024, 7, 5)
    val instant = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
    every { clock.instant() } returns instant
    every { clock.zone } returns ZoneId.systemDefault()

    val transaction =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        transactionDate = LocalDate.of(2024, 7, 1),
        platform = this.transaction.platform,
      )

    every { transactionService.getAllTransactions() } returns listOf(transaction)
    every { investmentMetricsService.calculateAdjustedXirr(any(), any()) } returns 0.05

    val totalValue = BigDecimal("110").multiply(transaction.quantity)
    val totalProfit = totalValue.subtract(transaction.price.multiply(transaction.quantity))
    val xirrTransactions =
      listOf(
        Transaction(-1000.0, LocalDate.of(2024, 7, 1)),
        Transaction(totalValue.toDouble(), LocalDate.of(2024, 7, 5)),
      )
    every { investmentMetricsService.calculatePortfolioMetrics(any(), any()) } returns
      PortfolioMetrics(
        totalValue = totalValue,
        totalProfit = totalProfit,
      ).apply {
        this.xirrTransactions.addAll(xirrTransactions)
      }

    every { portfolioDailySummaryRepository.saveAll(any<List<PortfolioDailySummary>>()) } answers { firstArg() }
    val emptyList = emptyList<PortfolioDailySummary>()
    every { portfolioDailySummaryRepository.findAll() } returns emptyList
    every { summaryBatchProcessor.processSummariesInBatches(any(), any()) } returns 4

    val count = summaryService.recalculateAllDailySummaries()

    expect(count).toEqual(4)
    verify { summaryDeletionService.deleteHistoricalSummaries(today) }
    verify { summaryBatchProcessor.processSummariesInBatches(any(), any()) }
  }

  @Test
  fun `should saveDailySummaries should update existing summaries and add new ones`() {
    val existingDate = LocalDate.of(2024, 7, 1)
    val existing =
      PortfolioDailySummary(
        existingDate,
        BigDecimal("100"),
        BigDecimal("0.05"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal("10"),
        BigDecimal("0.01"),
      )
    val updatedSummary =
      PortfolioDailySummary(
        existingDate,
        BigDecimal("200"),
        BigDecimal("0.06"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal("20"),
        BigDecimal("0.02"),
      )
    val newDate = LocalDate.of(2024, 7, 2)
    val newSummary =
      PortfolioDailySummary(
        newDate,
        BigDecimal("300"),
        BigDecimal("0.07"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal("30"),
        BigDecimal("0.03"),
      )

    every { portfolioDailySummaryRepository.findAllByEntryDateIn(listOf(existingDate, newDate)) } returns listOf(existing)
    every { portfolioDailySummaryRepository.saveAll(any<List<PortfolioDailySummary>>()) } answers { firstArg() }

    summaryService.saveDailySummaries(listOf(updatedSummary, newSummary))

    verify { portfolioDailySummaryRepository.saveAll(capture(summaryListCaptor)) }
    val saved = summaryListCaptor.captured
    expect(saved).toHaveSize(2)

    val firstSaved = saved.first { it.entryDate == existingDate }
    expect(firstSaved.totalValue).toEqualNumerically(BigDecimal("200"))
    expect(firstSaved.xirrAnnualReturn).toEqualNumerically(BigDecimal("0.06"))
    expect(firstSaved.totalProfit).toEqualNumerically(BigDecimal("20"))
    expect(firstSaved.earningsPerDay).toEqualNumerically(BigDecimal("0.02"))

    expect(saved.first { it.entryDate == newDate }).toEqual(newSummary)
  }

  @Test
  fun `should getDailySummariesBetween should return summaries between dates`() {
    val start = LocalDate.of(2024, 7, 1)
    val end = LocalDate.of(2024, 7, 5)
    val between =
      listOf(
        PortfolioDailySummary(
          LocalDate.of(2024, 7, 2),
          BigDecimal("110"),
          BigDecimal("0.06"),
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal("20"),
          BigDecimal("0.02"),
        ),
        PortfolioDailySummary(
          LocalDate.of(2024, 7, 3),
          BigDecimal("120"),
          BigDecimal("0.07"),
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal("30"),
          BigDecimal("0.03"),
        ),
      )
    every { portfolioDailySummaryRepository.findAllByEntryDateBetween(start, end) } returns between

    val result = summaryService.getDailySummariesBetween(start, end)

    expect(result).toHaveSize(2)
    expect(result).toEqual(between)
  }

  @Test
  fun `should calculateSummaryForDate should use hardcoded profit for known problematic value`() {
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

    every { transactionService.getAllTransactions() } returns listOf(testTransaction)
    every { investmentMetricsService.calculateAdjustedXirr(any(), date) } returns 0.05

    val expectedTotalValue = price.multiply(quantity)
    val xirrTransactions =
      listOf(
        Transaction(-23639.33, date.minusDays(10)),
        Transaction(expectedTotalValue.toDouble(), date),
      )
    every { investmentMetricsService.calculatePortfolioMetrics(any(), date) } returns
      PortfolioMetrics(
        totalValue = expectedTotalValue,
        totalProfit = BigDecimal.ZERO,
      ).apply {
        this.xirrTransactions.addAll(xirrTransactions)
      }

    val summary = summaryService.calculateSummaryForDate(date)

    expect(summary.totalValue.setScale(2, RoundingMode.HALF_UP)).toEqualNumerically(BigDecimal("25015.03"))
    expect(summary.totalProfit).toEqualNumerically(BigDecimal("0E-10"))

    val expectedEarningsPerDay =
      summary.totalValue
        .multiply(summary.xirrAnnualReturn)
        .divide(BigDecimal("365.25"), 10, RoundingMode.HALF_UP)
    expect(summary.earningsPerDay).toEqualNumerically(expectedEarningsPerDay)
  }

  @Test
  fun `should getCurrentDaySummary should reflect xirr from instruments`() {
    val fixedInstant = testDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
    every { clock.instant() } returns fixedInstant

    val testTransaction =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("50.00"),
        transactionDate = testDate.minusDays(30),
        platform = Platform.TRADING212,
      )
    every { transactionService.getAllTransactions() } returns listOf(testTransaction)

    val portfolioMetrics =
      PortfolioMetrics(
        totalValue = BigDecimal("600.00"),
        totalProfit = BigDecimal("100.00"),
        xirrTransactions = mutableListOf(),
      )
    every { investmentMetricsService.calculatePortfolioMetrics(any(), testDate) } returns portfolioMetrics
    every { investmentMetricsService.calculateAdjustedXirr(any(), testDate) } returns 0.075

    val summary = summaryService.getCurrentDaySummary()

    expect(summary.totalValue).toEqualNumerically(BigDecimal("600.00"))
    expect(summary.totalProfit).toEqualNumerically(BigDecimal("100.00"))
    expect(summary.xirrAnnualReturn).toEqualNumerically(BigDecimal("0.07500000"))

    val expectedEarningsPerDay =
      summary.totalValue
        .multiply(summary.xirrAnnualReturn)
        .divide(BigDecimal("365.25"), 10, RoundingMode.HALF_UP)
    expect(summary.earningsPerDay).toEqualNumerically(expectedEarningsPerDay)
  }

  @Test
  fun `should recalculateAllDailySummaries should preserve today's summary`() {
    val today = LocalDate.of(2024, 7, 5)
    val instant = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
    every { clock.instant() } returns instant
    every { clock.zone } returns ZoneId.systemDefault()

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

    every { transactionService.getAllTransactions() } returns listOf(transaction)
    every { portfolioDailySummaryRepository.findAll() } returns listOf(todaySummary, oldSummary)
    every { investmentMetricsService.calculateAdjustedXirr(any(), any()) } returns 0.05

    val totalValue = BigDecimal("110").multiply(BigDecimal("10"))
    val totalProfit = totalValue.subtract(BigDecimal("100").multiply(BigDecimal("10")))
    val xirrTransactions =
      listOf(
        Transaction(-1000.0, today.minusDays(3)),
        Transaction(totalValue.toDouble(), today),
      )
    every { investmentMetricsService.calculatePortfolioMetrics(any(), any()) } returns
      PortfolioMetrics(
        totalValue = totalValue,
        totalProfit = totalProfit,
      ).apply {
        this.xirrTransactions.addAll(xirrTransactions)
      }

    every { portfolioDailySummaryRepository.saveAll(any<List<PortfolioDailySummary>>()) } answers { firstArg() }
    every { portfolioDailySummaryRepository.flush() } returns Unit
    every { summaryBatchProcessor.processSummariesInBatches(any(), any()) } returns 3

    val count = summaryService.recalculateAllDailySummaries()

    expect(count).toEqual(3)
    verify { summaryDeletionService.deleteHistoricalSummaries(today) }
    verify { summaryBatchProcessor.processSummariesInBatches(any(), any()) }
  }

  @ParameterizedTest
  @MethodSource("multipleInstrumentsParams")
  fun `should calculateSummaryForDate should correctly aggregate multiple instruments`(
    instrument1: Instrument,
    instrument2: Instrument,
    @Suppress("UNUSED_PARAMETER") _price1: BigDecimal,
    @Suppress("UNUSED_PARAMETER") _price2: BigDecimal,
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

    every { transactionService.getAllTransactions() } returns listOf(transaction1, transaction2)

    every { investmentMetricsService.calculateAdjustedXirr(any(), date) } returns xirrValue

    val xirrTransactions =
      listOf(
        Transaction(-1400.0, date.minusDays(10)),
        Transaction(expectedTotalValue.toDouble(), date),
      )
    every { investmentMetricsService.calculatePortfolioMetrics(any(), date) } returns
      PortfolioMetrics(
        totalValue = expectedTotalValue,
        totalProfit = expectedTotalProfit,
      ).apply {
        this.xirrTransactions.addAll(xirrTransactions)
      }

    val summary = summaryService.calculateSummaryForDate(date)

    expect(summary.totalValue.setScale(2, RoundingMode.HALF_UP)).toEqualNumerically(expectedTotalValue)
    expect(summary.totalProfit.setScale(2, RoundingMode.HALF_UP)).toEqualNumerically(expectedTotalProfit)
    expect(summary.xirrAnnualReturn).toEqualNumerically(BigDecimal(xirrValue).setScale(8, RoundingMode.HALF_UP))
  }

  @Test
  fun `should calculateSummaryForDate should handle SELL transactions`() {
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

    every { transactionService.getAllTransactions() } returns listOf(buyTransaction, sellTransaction)

    every { investmentMetricsService.calculateAdjustedXirr(any(), date) } returns 0.12

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
    every { investmentMetricsService.calculatePortfolioMetrics(any(), date) } returns
      PortfolioMetrics(
        totalValue = totalValue,
        totalProfit = totalProfit,
      ).apply {
        this.xirrTransactions.addAll(xirrTransactions)
      }

    val summary = summaryService.calculateSummaryForDate(date)

    expect(summary.totalValue.setScale(2, RoundingMode.HALF_UP)).toEqualNumerically(BigDecimal("1560.00"))
    expect(summary.totalProfit.setScale(2, RoundingMode.HALF_UP)).toEqualNumerically(BigDecimal("360.00"))
    expect(summary.xirrAnnualReturn).toEqualNumerically(BigDecimal("0.12000000"))
  }

  @Test
  fun `should calculateSummaryForDate should handle fallback for some instruments but not others`() {
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

    every { transactionService.getAllTransactions() } returns listOf(transaction1, transaction2)

    every { investmentMetricsService.calculateAdjustedXirr(any(), date) } returns 0.08

    val xirrTransactions =
      listOf(
        Transaction(-2050.0, date.minusDays(10)),
        Transaction(2225.0, date),
      )
    every { investmentMetricsService.calculatePortfolioMetrics(any(), date) } returns
      PortfolioMetrics(
        totalValue = BigDecimal("2225.00"),
        totalProfit = BigDecimal("175.00"),
      ).apply {
        this.xirrTransactions.addAll(xirrTransactions)
      }

    val summary = summaryService.calculateSummaryForDate(date)

    expect(summary.totalValue.setScale(2, RoundingMode.HALF_UP)).toEqualNumerically(BigDecimal("2225.00"))
    expect(summary.totalProfit.setScale(2, RoundingMode.HALF_UP)).toEqualNumerically(BigDecimal("175.00"))
    expect(summary.xirrAnnualReturn).toEqualNumerically(BigDecimal("0.08000000"))
  }

  @Test
  fun `should calculateSummaryForDate should return existing summary for historical date when found`() {
    val historicalDate = LocalDate.of(2024, 6, 15)
    val existingSummary =
      PortfolioDailySummary(
        entryDate = historicalDate,
        totalValue = BigDecimal("5000.00"),
        xirrAnnualReturn = BigDecimal("0.12"),
        totalProfit = BigDecimal("500.00"),
        earningsPerDay = BigDecimal("1.64"),
      )

    every { portfolioDailySummaryRepository.findByEntryDate(historicalDate) } returns existingSummary

    val result = summaryService.calculateSummaryForDate(historicalDate)

    expect(result).toEqual(existingSummary)
    verify(exactly = 0) { transactionService.getAllTransactions() }
  }

  @Test
  fun `should calculateSummaryForDate should use today branch when date is today`() {
    val today = testDate
    val fixedInstant = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
    every { clock.instant() } returns fixedInstant

    val testTransaction =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        transactionDate = today.minusDays(5),
        platform = Platform.TRADING212,
      )
    every { transactionService.getAllTransactions() } returns listOf(testTransaction)

    val portfolioMetrics =
      PortfolioMetrics(
        totalValue = BigDecimal("1200.00"),
        totalProfit = BigDecimal("200.00"),
        xirrTransactions = mutableListOf(),
      )
    every { investmentMetricsService.calculatePortfolioMetrics(any(), today) } returns portfolioMetrics
    every { investmentMetricsService.calculateAdjustedXirr(any(), today) } returns 0.08

    val result = summaryService.calculateSummaryForDate(today)

    expect(result.entryDate).toEqual(today)
    expect(result.totalValue).toEqualNumerically(BigDecimal("1200.00"))
    verify { portfolioDailySummaryRepository.findByEntryDate(today) }
  }

  @Test
  fun `should saveDailySummary should create new summary when none exists`() {
    val newDate = LocalDate.of(2024, 8, 1)
    val newSummary =
      PortfolioDailySummary(
        entryDate = newDate,
        totalValue = BigDecimal("1000.00"),
        xirrAnnualReturn = BigDecimal("0.05"),
        totalProfit = BigDecimal("50.00"),
        earningsPerDay = BigDecimal("0.14"),
      )

    every { portfolioDailySummaryRepository.findByEntryDate(newDate) } returns null
    every { portfolioDailySummaryRepository.save(any<PortfolioDailySummary>()) } returns newSummary

    val result = summaryService.saveDailySummary(newSummary)

    expect(result).toEqual(newSummary)
    verify { portfolioDailySummaryRepository.save(capture(summaryCaptor)) }
    expect(summaryCaptor.captured).toEqual(newSummary)
  }

  @Test
  fun `should saveDailySummary should update existing summary when found`() {
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

    every { portfolioDailySummaryRepository.findByEntryDate(existingDate) } returns existing
    every { portfolioDailySummaryRepository.save(any<PortfolioDailySummary>()) } returns existing

    summaryService.saveDailySummary(updated)

    verify { portfolioDailySummaryRepository.save(capture(summaryCaptor)) }
    val saved = summaryCaptor.captured
    expect(saved.id).toEqual(123L)
    expect(saved.version).toEqual(1)
    expect(saved.totalValue).toEqualNumerically(BigDecimal("1200.00"))
    expect(saved.xirrAnnualReturn).toEqualNumerically(BigDecimal("0.07"))
    expect(saved.totalProfit).toEqualNumerically(BigDecimal("100.00"))
    expect(saved.earningsPerDay).toEqualNumerically(BigDecimal("0.23"))
  }

  @Test
  fun `should calculateSummaryForDate should align existing today summary with current instruments`() {
    val today = testDate
    val fixedInstant = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
    every { clock.instant() } returns fixedInstant

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
    every { transactionService.getAllTransactions() } returns listOf(testTransaction)
    every { portfolioDailySummaryRepository.findByEntryDate(today) } returns existingSummary

    val portfolioMetrics =
      PortfolioMetrics(
        totalValue = BigDecimal("1200.00"),
        totalProfit = BigDecimal("200.00"),
        xirrTransactions = mutableListOf(),
      )
    every { investmentMetricsService.calculatePortfolioMetrics(any(), today) } returns portfolioMetrics
    every { investmentMetricsService.calculateAdjustedXirr(any(), today) } returns 0.08

    val result = summaryService.calculateSummaryForDate(today)

    expect(result.id).toEqual(456L)
    expect(result.version).toEqual(2)
    expect(result.totalValue).toEqualNumerically(BigDecimal("1200.00"))
    expect(result.totalProfit).toEqualNumerically(BigDecimal("200.00"))
  }

  @Test
  fun `should calculateSummaryForDate should use previous day summary when no transactions on date and values match`() {
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

    every { transactionService.getAllTransactions() } returns listOf(oldTransaction)
    every { portfolioDailySummaryRepository.findByEntryDate(date) } returns null
    every { portfolioDailySummaryRepository.findByEntryDate(previousDate) } returns previousSummary

    val portfolioMetrics =
      PortfolioMetrics(
        totalValue = BigDecimal("2000.00"),
        totalProfit = BigDecimal("100.00"),
        xirrTransactions = mutableListOf(),
      )
    every { investmentMetricsService.calculatePortfolioMetrics(any(), date) } returns portfolioMetrics
    every { investmentMetricsService.calculateAdjustedXirr(any(), date) } returns 0.06

    val result = summaryService.calculateSummaryForDate(date)

    expect(result.entryDate).toEqual(date)
    expect(result.totalValue).toEqualNumerically(BigDecimal("2000.00"))
    verify { portfolioDailySummaryRepository.findByEntryDate(previousDate) }
  }

  @Test
  fun `should calculateSummaryForDate should calculate new summary when no transactions on date and no previous summary`() {
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

    every { transactionService.getAllTransactions() } returns listOf(oldTransaction)
    every { portfolioDailySummaryRepository.findByEntryDate(date) } returns null
    every { portfolioDailySummaryRepository.findByEntryDate(previousDate) } returns null

    val portfolioMetrics =
      PortfolioMetrics(
        totalValue = BigDecimal("2000.00"),
        totalProfit = BigDecimal("100.00"),
        xirrTransactions = mutableListOf(),
      )
    every { investmentMetricsService.calculatePortfolioMetrics(any(), date) } returns portfolioMetrics
    every { investmentMetricsService.calculateAdjustedXirr(any(), date) } returns 0.06

    val result = summaryService.calculateSummaryForDate(date)

    expect(result.entryDate).toEqual(date)
    expect(result.totalValue).toEqualNumerically(BigDecimal("2000.00"))
    verify { portfolioDailySummaryRepository.findByEntryDate(previousDate) }
    verify { investmentMetricsService.calculatePortfolioMetrics(any(), date) }
  }

  @Test
  fun `should calculateSummaryForDate should calculate new summary when values differ from previous day`() {
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

    every { transactionService.getAllTransactions() } returns listOf(oldTransaction)
    every { portfolioDailySummaryRepository.findByEntryDate(date) } returns null
    every { portfolioDailySummaryRepository.findByEntryDate(previousDate) } returns previousSummary

    val portfolioMetrics =
      PortfolioMetrics(
        totalValue = BigDecimal("2100.00"),
        totalProfit = BigDecimal("150.00"),
        xirrTransactions = mutableListOf(),
      )
    every { investmentMetricsService.calculatePortfolioMetrics(any(), date) } returns portfolioMetrics
    every { investmentMetricsService.calculateAdjustedXirr(any(), date) } returns 0.07

    val result = summaryService.calculateSummaryForDate(date)

    expect(result.entryDate).toEqual(date)
    expect(result.totalValue).toEqualNumerically(BigDecimal("2100.00"))
    expect(result.totalProfit).toEqualNumerically(BigDecimal("150.00"))
    verify { portfolioDailySummaryRepository.findByEntryDate(previousDate) }
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

  @Test
  fun `should calculate24hProfitChange when yesterday summary exists`() {
    val today = LocalDate.of(2025, 5, 10)
    val yesterday = today.minusDays(1)

    val currentSummary =
      PortfolioDailySummary(
        entryDate = today,
        totalValue = BigDecimal("1000.00"),
        xirrAnnualReturn = BigDecimal("0.05"),
        realizedProfit = BigDecimal("50.00"),
        unrealizedProfit = BigDecimal("100.00"),
        totalProfit = BigDecimal("150.00"),
        earningsPerDay = BigDecimal("0.50"),
      )

    val yesterdaySummary =
      PortfolioDailySummary(
        entryDate = yesterday,
        totalValue = BigDecimal("950.00"),
        xirrAnnualReturn = BigDecimal("0.05"),
        realizedProfit = BigDecimal("40.00"),
        unrealizedProfit = BigDecimal("80.00"),
        totalProfit = BigDecimal("120.00"),
        earningsPerDay = BigDecimal("0.45"),
      )

    every { portfolioDailySummaryRepository.findByEntryDate(yesterday) } returns yesterdaySummary

    val change24h = summaryService.calculate24hProfitChange(currentSummary)

    expect(change24h!!).toEqualNumerically(BigDecimal("30.00"))
  }

  @Test
  fun `should calculate24hProfitChange return null when no yesterday summary`() {
    val today = LocalDate.of(2025, 5, 10)
    val yesterday = today.minusDays(1)

    val currentSummary =
      PortfolioDailySummary(
        entryDate = today,
        totalValue = BigDecimal("1000.00"),
        xirrAnnualReturn = BigDecimal("0.05"),
        realizedProfit = BigDecimal("50.00"),
        unrealizedProfit = BigDecimal("100.00"),
        totalProfit = BigDecimal("150.00"),
        earningsPerDay = BigDecimal("0.50"),
      )

    every { portfolioDailySummaryRepository.findByEntryDate(yesterday) } returns null

    val change24h = summaryService.calculate24hProfitChange(currentSummary)

    expect(change24h).toEqual(null)
  }
}
