package ee.tenman.portfolio.service.calculation

import ch.tutteli.atrium.api.fluent.en_GB.notToBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.calculation.xirr.Xirr
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.summary.SummaryService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import java.util.stream.Stream
import kotlin.math.abs

class CalculationServiceTest {
  private val dataRetrievalService = mockk<DailyPriceService>()
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val clock = mockk<Clock>()
  private val portfolioSummaryService = mockk<SummaryService>()

  private lateinit var calculationService: CalculationService
  private lateinit var testInstrument: Instrument
  private lateinit var testDispatcher: CoroutineDispatcher
  private val today = LocalDate.of(2024, 5, 15)
  private val instrumentCode = "QDVE:GER:EUR"

  @BeforeEach
  fun setUp() {
    testDispatcher = Dispatchers.Unconfined
    testInstrument = createTestInstrument()
    every { clock.instant() } returns today.atStartOfDay(ZoneId.systemDefault()).toInstant()
    every { clock.zone } returns ZoneId.systemDefault()

    calculationService =
      CalculationService(
        dataRetrievalService = dataRetrievalService,
        instrumentRepository = instrumentRepository,
        calculationDispatcher = testDispatcher,
        clock = clock,
        portfolioSummaryService = portfolioSummaryService,
      )

    every { instrumentRepository.findBySymbol(instrumentCode) } returns Optional.of(testInstrument)
  }

  private fun createTestInstrument() =
    Instrument(
      symbol = instrumentCode,
      name = "iShares S&P 500 Information Technology Sector",
      category = "ETF",
      baseCurrency = "EUR",
      providerName = ProviderName.FT,
    ).apply {
      id = 1L
      currentPrice = BigDecimal("28.50")
    }

  @Test
  fun `should return empty list when no daily prices exist`() {
    every { dataRetrievalService.findAllByInstrument(testInstrument) } returns emptyList()

    val result = calculationService.calculateRollingXirr(instrumentCode)

    expect(result).toBeEmpty()
  }

  @Test
  fun `should return empty list when only one daily price exists`() {
    val singlePrice = createDailyPrice(today.minusDays(10), BigDecimal("25.0"))
    every { dataRetrievalService.findAllByInstrument(testInstrument) } returns listOf(singlePrice)

    val result = calculationService.calculateRollingXirr(instrumentCode)

    expect(result).toBeEmpty()
  }

  @ParameterizedTest(name = "Price trend: {0}")
  @MethodSource("providePriceTrends")
  fun `should calculates appropriate XIRR values for different price trends`(
    @Suppress("UNUSED_PARAMETER") _trendDescription: String,
    startPrice: Double,
    endPrice: Double,
    expectedXirrCondition: (Double) -> Boolean,
  ) {
    val prices =
      createPriceHistory(
        startDate = today.minusDays(60),
        startPrice = startPrice,
        endPrice = endPrice,
        ascending = endPrice > startPrice,
      )
    every { dataRetrievalService.findAllByInstrument(testInstrument) } returns prices

    val result = calculationService.calculateRollingXirr(instrumentCode)

    expect(result).notToBeEmpty()
    expect(result[0].getCashFlows().size).toBeGreaterThanOrEqualTo(2)
    expect(result[0].getCashFlows().last().amount).toBeGreaterThan(0.0)

    val xirrValue = result[0].calculate()
    expect(expectedXirrCondition(xirrValue)).toEqual(true)
  }

  @Test
  fun `should creates multiple time windows at two-week intervals`() {
    val prices =
      createPriceHistory(
        startDate = today.minusMonths(6),
        startPrice = 20.0,
        endPrice = 35.0,
      )
    every { dataRetrievalService.findAllByInstrument(testInstrument) } returns prices

    val result = calculationService.calculateRollingXirr(instrumentCode)

    expect(result).notToBeEmpty()

    val endDates =
      result
        .map { it.getCashFlows().maxOf { tx -> tx.date } }
        .distinct()
        .sorted()

    expect(endDates.size).toBeGreaterThan(1)
    expect(endDates[1].toEpochDay() - endDates[0].toEpochDay()).toEqual(15)
  }

  @ParameterizedTest(name = "Price scenario: {0}")
  @MethodSource("providePriceScenarios")
  fun `should handles various price scenarios appropriately`(
    @Suppress("UNUSED_PARAMETER") _scenarioName: String,
    prices: List<Pair<Int, Double>>,
    validator: (List<Xirr>) -> Unit,
  ) {
    val dailyPrices =
      prices.map { (daysAgo, price) ->
        createDailyPrice(today.minusDays(daysAgo.toLong()), BigDecimal(price.toString()))
      }

    every { dataRetrievalService.findAllByInstrument(testInstrument) } returns dailyPrices

    val result = calculationService.calculateRollingXirr(instrumentCode)

    validator(result)
  }

  @Test
  fun `should correctly creates buy and hold transactions`() {
    val stablePrices =
      listOf(
        createDailyPrice(today.minusDays(60), BigDecimal("100.0")),
        createDailyPrice(today.minusDays(40), BigDecimal("100.0")),
        createDailyPrice(today.minusDays(20), BigDecimal("100.0")),
        createDailyPrice(today.minusDays(10), BigDecimal("100.0")),
        createDailyPrice(today, BigDecimal("100.0")),
      )
    every { dataRetrievalService.findAllByInstrument(testInstrument) } returns stablePrices

    val result = calculationService.calculateRollingXirr(instrumentCode)

    expect(result).notToBeEmpty()

    val transactions = result[0].getCashFlows().sortedBy { it.date }
    expect(transactions).toHaveSize(2)
    expect(transactions[0].amount).toEqual(-1000.0)
    expect(transactions[1].amount).toEqual(1000.0)
  }

  private fun createDailyPrice(
    date: LocalDate,
    price: BigDecimal,
  ): DailyPrice =
    DailyPrice(
      instrument = testInstrument,
      entryDate = date,
      providerName = ProviderName.FT,
      openPrice = price,
      highPrice = price,
      lowPrice = price,
      closePrice = price,
      volume = 1000L,
    )

  private fun createPriceHistory(
    startDate: LocalDate,
    startPrice: Double,
    endPrice: Double,
    ascending: Boolean = true,
  ): List<DailyPrice> {
    val dates =
      listOf(
        startDate,
        startDate.plusDays(15),
        startDate.plusDays(30),
        startDate.plusDays(45),
        today,
      )

    return dates.mapIndexed { index, date ->
      val ratio = index / 4.0
      val price =
        if (ascending) {
          startPrice + ((endPrice - startPrice) * ratio)
        } else {
          endPrice + ((startPrice - endPrice) * (1 - ratio))
        }
      createDailyPrice(date, BigDecimal(price))
    }
  }

  companion object {
    @JvmStatic
    fun providePriceTrends(): Stream<Arguments> =
      Stream.of(
        Arguments.of(
          "Upward trend",
          25.0,
          30.0,
          { xirr: Double -> xirr > 0 },
        ),
        Arguments.of(
          "Downward trend",
          30.0,
          20.0,
          { xirr: Double -> xirr < 0 && xirr > -1.0 },
        ),
        Arguments.of(
          "Stable prices",
          100.0,
          100.0,
          { xirr: Double -> abs(xirr) < 0.0001 },
        ),
      )

    @JvmStatic
    fun providePriceScenarios(): Stream<Arguments> =
      Stream.of(
        Arguments.of(
          "Extreme high price",
          listOf(Pair(30, 25.0), Pair(20, 1000000.0)),
          { result: List<Xirr> ->
            result.forEach { xirr ->
              expect(xirr.calculate()).toBeGreaterThan(-1.0)
              expect(xirr.getCashFlows().last().amount).toBeGreaterThan(0.0)
            }
          },
        ),
        Arguments.of(
          "Very low ending price",
          listOf(Pair(30, 25.0), Pair(20, 20.0), Pair(10, 0.01)),
          { result: List<Xirr> ->
            result.forEach { xirr ->
              expect(xirr.calculate()).toBeLessThanOrEqualTo(0.99)
            }
          },
        ),
        Arguments.of(
          "Market-like fluctuations",
          listOf(
            Pair(50, 98.50),
            Pair(40, 102.30),
            Pair(30, 99.75),
            Pair(20, 105.40),
            Pair(10, 103.80),
          ),
          { result: List<Xirr> ->
            expect(result).notToBeEmpty()
            val xirrValue = result[0].calculate()
            expect(xirrValue).toBeGreaterThan(-1.0)
            expect(xirrValue).toBeLessThan(10.0)
          },
        ),
      )
  }

  @Test
  fun `should calculateBatchXirrAsync processes single date successfully`() {
    runBlocking {
      val dates = listOf(today)
      val summary = createTestSummary(today)

      every { portfolioSummaryService.calculateSummaryForDate(today) } returns summary
      every { portfolioSummaryService.saveDailySummary(summary) } returns summary

      val result = calculationService.calculateBatchXirrAsync(dates)

      expect(result.processedDates).toEqual(1)
      expect(result.failedCalculations).toBeEmpty()
      expect(result.duration).toBeGreaterThanOrEqualTo(0)
      verify { portfolioSummaryService.calculateSummaryForDate(today) }
      verify { portfolioSummaryService.saveDailySummary(summary) }
    }
  }

  @Test
  fun `should calculateBatchXirrAsync processes multiple dates successfully`() {
    runBlocking {
      val dates =
        listOf(
          today,
          today.plusDays(1),
          today.plusDays(2),
        )

      dates.forEach { date ->
        val summary = createTestSummary(date)
        every { portfolioSummaryService.calculateSummaryForDate(date) } returns summary
        every { portfolioSummaryService.saveDailySummary(summary) } returns summary
      }

      val result = calculationService.calculateBatchXirrAsync(dates)

      expect(result.processedDates).toEqual(3)
      expect(result.processedInstruments).toEqual(0)
      expect(result.failedCalculations).toBeEmpty()
      verify(exactly = 3) { portfolioSummaryService.calculateSummaryForDate(any()) }
      verify(exactly = 3) { portfolioSummaryService.saveDailySummary(any()) }
    }
  }

  @Test
  fun `should calculateBatchXirrAsync handles calculation failure`() {
    runBlocking {
      val successDate = today
      val failDate = today.plusDays(1)
      val dates = listOf(successDate, failDate)

      val summary = createTestSummary(successDate)
      every { portfolioSummaryService.calculateSummaryForDate(successDate) } returns summary
      every { portfolioSummaryService.saveDailySummary(summary) } returns summary
      every { portfolioSummaryService.calculateSummaryForDate(failDate) } throws RuntimeException("Calculation failed")

      val result = calculationService.calculateBatchXirrAsync(dates)

      expect(result.processedDates).toEqual(1)
      expect(result.failedCalculations).toHaveSize(1)
      expect(result.failedCalculations[0]).toContain("Failed for date")
    }
  }

  @Test
  fun `should calculateBatchXirrAsync handles empty date list`() {
    runBlocking {
      val result = calculationService.calculateBatchXirrAsync(emptyList())

      expect(result.processedDates).toEqual(0)
      expect(result.failedCalculations).toBeEmpty()
      expect(result.duration).toBeGreaterThanOrEqualTo(0)
    }
  }

  private fun createTestSummary(date: LocalDate): PortfolioDailySummary =
    PortfolioDailySummary(
      entryDate = date,
      totalValue = BigDecimal("10000"),
      xirrAnnualReturn = BigDecimal("0.15"),
      totalProfit = BigDecimal("2000"),
      earningsPerDay = BigDecimal("5.48"),
    )

  @Test
  fun `should calculateMedian returns 0 when list is empty`() {
    val result = calculationService.calculateMedian(emptyList())

    expect(result).toEqual(0.0)
  }

  @Test
  fun `should calculateMedian returns the only element when list has single element`() {
    val result = calculationService.calculateMedian(listOf(42.5))

    expect(result).toEqual(42.5)
  }

  @Test
  fun `should calculateMedian returns average of two middle elements when list has two elements`() {
    val result = calculationService.calculateMedian(listOf(10.0, 20.0))

    expect(result).toEqual(15.0)
  }

  @Test
  fun `should calculateMedian returns middle element when list has odd number of elements`() {
    val result = calculationService.calculateMedian(listOf(5.0, 10.0, 15.0, 20.0, 25.0))

    expect(result).toEqual(15.0)
  }

  @Test
  fun `should calculateMedian returns average of two middle elements when list has even number of elements`() {
    val result = calculationService.calculateMedian(listOf(5.0, 10.0, 20.0, 25.0))

    expect(result).toEqual(15.0)
  }

  @Test
  fun `should calculateMedian handles unsorted list correctly`() {
    val result = calculationService.calculateMedian(listOf(25.0, 5.0, 15.0, 10.0, 20.0))

    expect(result).toEqual(15.0)
  }

  @Test
  fun `should calculateRollingXirr breaks loop when filtering results in less than 2 prices`() {
    val prices =
      listOf(
        createDailyPrice(today.minusMonths(3), BigDecimal("100.0")),
        createDailyPrice(today.minusMonths(2), BigDecimal("110.0")),
      )
    every { dataRetrievalService.findAllByInstrument(testInstrument) } returns prices

    val result = calculationService.calculateRollingXirr(instrumentCode)

    expect(result).notToBeEmpty()
  }

  @Test
  fun `should calculateRollingXirr excludes xirr when currentValue is zero`() {
    val prices =
      listOf(
        createDailyPrice(today.minusDays(60), BigDecimal("100.0")),
        createDailyPrice(today.minusDays(40), BigDecimal("50.0")),
        createDailyPrice(today.minusDays(20), BigDecimal("0.0")),
      )
    every { dataRetrievalService.findAllByInstrument(testInstrument) } returns prices

    val result = calculationService.calculateRollingXirr(instrumentCode)

    result.forEach { xirr ->
      val lastTransaction = xirr.getCashFlows().last()
      expect(lastTransaction.amount).toBeGreaterThan(0.0)
    }
  }

  @Test
  fun `should calculateRollingXirr filters out xirr calculations that throw exceptions`() {
    val prices =
      listOf(
        createDailyPrice(today.minusDays(60), BigDecimal("0.0")),
        createDailyPrice(today.minusDays(40), BigDecimal("0.0")),
        createDailyPrice(today.minusDays(20), BigDecimal("100.0")),
      )
    every { dataRetrievalService.findAllByInstrument(testInstrument) } returns prices

    val result = calculationService.calculateRollingXirr(instrumentCode)

    result.forEach { xirr ->
      expect(xirr.calculate()).toBeGreaterThan(-1.0)
    }
  }

  @Test
  fun `should getCalculationResult returns zeros when no valid xirr results exist`() {
    val prices =
      listOf(
        createDailyPrice(today.minusDays(30), BigDecimal("100.0")),
        createDailyPrice(today.minusDays(20), BigDecimal("0.0")),
      )
    every { dataRetrievalService.findAllByInstrument(testInstrument) } returns prices

    val result = calculationService.getCalculationResult()

    expect(result.median).toEqual(0.0)
    expect(result.average).toEqual(0.0)
    expect(result.cashFlows).toBeEmpty()
  }

  @Test
  fun `should getCalculationResult filters out xirr values less than or equal to -1`() {
    val prices =
      listOf(
        createDailyPrice(today.minusDays(60), BigDecimal("1000.0")),
        createDailyPrice(today.minusDays(40), BigDecimal("500.0")),
        createDailyPrice(today.minusDays(20), BigDecimal("1.0")),
      )
    every { dataRetrievalService.findAllByInstrument(testInstrument) } returns prices

    val result = calculationService.getCalculationResult()

    result.cashFlows.forEach { cashFlow ->
      val correspondingXirr =
        calculationService
          .calculateRollingXirr(instrumentCode)
          .find { it.getCashFlows().maxOf { tx -> tx.date } == cashFlow.date }
      if (correspondingXirr != null) {
        expect(correspondingXirr.calculate()).toBeGreaterThan(-1.0)
      }
    }
  }

  @Test
  fun `should getCalculationResult uses calculation dispatcher for async operations`() {
    val prices =
      createPriceHistory(
        startDate = today.minusDays(60),
        startPrice = 20.0,
        endPrice = 30.0,
      )
    every { dataRetrievalService.findAllByInstrument(testInstrument) } returns prices

    val result = calculationService.getCalculationResult()

    expect(result.median).toBeGreaterThan(0.0)
    expect(result.average).toBeGreaterThan(0.0)
    expect(result.cashFlows).notToBeEmpty()
  }

  @Test
  fun `should calculateBatchXirrAsync handles all failures correctly`() {
    runBlocking {
      val dates = listOf(today, today.plusDays(1), today.plusDays(2))

      dates.forEach { date ->
        every { portfolioSummaryService.calculateSummaryForDate(date) } throws RuntimeException("Calculation failed for $date")
      }

      val result = calculationService.calculateBatchXirrAsync(dates)

      expect(result.processedDates).toEqual(0)
      expect(result.failedCalculations).toHaveSize(3)
      result.failedCalculations.forEach { expect(it).toContain("Failed for date") }
      expect(result.duration).toBeGreaterThanOrEqualTo(0)
    }
  }

  @Test
  fun `should calculateBatchXirrAsync calculates duration correctly`() {
    runBlocking {
      val dates = listOf(today)
      val summary = createTestSummary(today)
      every { portfolioSummaryService.calculateSummaryForDate(today) } returns summary
      every { portfolioSummaryService.saveDailySummary(summary) } returns summary

      val startTime = System.currentTimeMillis()
      val result = calculationService.calculateBatchXirrAsync(dates)
      val endTime = System.currentTimeMillis()

      expect(result.duration).toBeGreaterThanOrEqualTo(0)
      expect(result.duration).toBeLessThanOrEqualTo(endTime - startTime + 100)
    }
  }

  @Test
  fun `should calculateBatchXirrAsync processes mixed success and failure scenarios`() {
    runBlocking {
      val date1 = today
      val date2 = today.plusDays(1)
      val date3 = today.plusDays(2)
      val dates = listOf(date1, date2, date3)

      val summary1 = createTestSummary(date1)
      val summary3 = createTestSummary(date3)

      every { portfolioSummaryService.calculateSummaryForDate(date1) } returns summary1
      every { portfolioSummaryService.saveDailySummary(summary1) } returns summary1
      every { portfolioSummaryService.calculateSummaryForDate(date2) } throws RuntimeException("Failed for date2")
      every { portfolioSummaryService.calculateSummaryForDate(date3) } returns summary3
      every { portfolioSummaryService.saveDailySummary(summary3) } returns summary3

      val result = calculationService.calculateBatchXirrAsync(dates)

      expect(result.processedDates).toEqual(2)
      expect(result.failedCalculations).toHaveSize(1)
      expect(result.failedCalculations[0]).toContain("Failed for date $date2")
      verify { portfolioSummaryService.saveDailySummary(summary1) }
      verify { portfolioSummaryService.saveDailySummary(summary3) }
    }
  }

  @Test
  fun `should calculateRollingXirr throws exception when instrument not found`() {
    every { instrumentRepository.findBySymbol("UNKNOWN") } returns Optional.empty()

    val exception =
      org.junit.jupiter.api.assertThrows<RuntimeException> {
        calculationService.calculateRollingXirr("UNKNOWN")
      }

    expect(exception.message!!).toContain("Instrument not found with symbol: UNKNOWN")
  }
}
