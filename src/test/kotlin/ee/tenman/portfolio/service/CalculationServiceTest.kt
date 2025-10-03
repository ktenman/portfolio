package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.xirr.Xirr
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional
import java.util.stream.Stream
import kotlin.math.abs

@ExtendWith(MockitoExtension::class)
class CalculationServiceTest {
  @Mock
  private lateinit var dataRetrievalService: DailyPriceService

  @Mock
  private lateinit var instrumentRepository: InstrumentRepository

  @Mock
  private lateinit var clock: Clock

  @Mock
  private lateinit var portfolioSummaryService: SummaryService

  private lateinit var calculationService: CalculationService
  private lateinit var testInstrument: Instrument
  private lateinit var testDispatcher: CoroutineDispatcher
  private val today = LocalDate.of(2024, 5, 15)
  private val instrumentCode = "QDVE:GER:EUR"

  @BeforeEach
  fun setUp() {
    testDispatcher = Dispatchers.Unconfined
    testInstrument = createTestInstrument()
    lenient().whenever(clock.instant()).thenReturn(today.atStartOfDay(ZoneId.systemDefault()).toInstant())
    lenient().whenever(clock.zone).thenReturn(ZoneId.systemDefault())

    calculationService =
      CalculationService(
        dataRetrievalService = dataRetrievalService,
        instrumentRepository = instrumentRepository,
        calculationDispatcher = testDispatcher,
        clock = clock,
        portfolioSummaryService = portfolioSummaryService,
      )

    whenever(instrumentRepository.findBySymbol(instrumentCode)).thenReturn(Optional.of(testInstrument))
  }

  private fun createTestInstrument() =
    Instrument(
      symbol = instrumentCode,
      name = "iShares S&P 500 Information Technology Sector",
      category = "ETF",
      baseCurrency = "EUR",
      providerName = ProviderName.ALPHA_VANTAGE,
    ).apply {
      id = 1L
      currentPrice = BigDecimal("28.50")
    }

  @Test
  fun `returns empty list when no daily prices exist`() {
    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(emptyList())

    val result = calculationService.calculateRollingXirr(instrumentCode)

    assertThat(result).isEmpty()
  }

  @Test
  fun `returns empty list when only one daily price exists`() {
    val singlePrice = createDailyPrice(today.minusDays(10), BigDecimal("25.0"))
    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(listOf(singlePrice))

    val result = calculationService.calculateRollingXirr(instrumentCode)

    assertThat(result).isEmpty()
  }

  @ParameterizedTest(name = "Price trend: {0}")
  @MethodSource("providePriceTrends")
  fun `calculates appropriate XIRR values for different price trends`(
    trendDescription: String,
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
    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(prices)

    val result = calculationService.calculateRollingXirr(instrumentCode)

    assertThat(result).isNotEmpty()
    assertThat(result[0].getTransactions().size).isGreaterThanOrEqualTo(2)
    assertThat(result[0].getTransactions().last().amount).isGreaterThan(0.0)

    val xirrValue = result[0].calculate()
    assertThat(expectedXirrCondition(xirrValue)).isTrue()
  }

  @Test
  fun `creates multiple time windows at two-week intervals`() {
    val prices =
      createPriceHistory(
        startDate = today.minusMonths(6),
        startPrice = 20.0,
        endPrice = 35.0,
      )
    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(prices)

    val result = calculationService.calculateRollingXirr(instrumentCode)

    assertThat(result).isNotEmpty()

    val endDates =
      result
        .map { it.getTransactions().maxOf { tx -> tx.date } }
        .distinct()
        .sorted()

    assertThat(endDates.size).isGreaterThan(1)
    assertThat(endDates[1].toEpochDay() - endDates[0].toEpochDay()).isEqualTo(15)
  }

  @ParameterizedTest(name = "Price scenario: {0}")
  @MethodSource("providePriceScenarios")
  fun `handles various price scenarios appropriately`(
    scenarioName: String,
    prices: List<Pair<Int, Double>>,
    validator: (List<Xirr>) -> Unit,
  ) {
    val dailyPrices =
      prices.map { (daysAgo, price) ->
        createDailyPrice(today.minusDays(daysAgo.toLong()), BigDecimal(price.toString()))
      }

    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(dailyPrices)

    val result = calculationService.calculateRollingXirr(instrumentCode)

    validator(result)
  }

  @Test
  fun `correctly creates buy and hold transactions`() {
    val stablePrices =
      listOf(
        createDailyPrice(today.minusDays(60), BigDecimal("100.0")),
        createDailyPrice(today.minusDays(40), BigDecimal("100.0")),
        createDailyPrice(today.minusDays(20), BigDecimal("100.0")),
        createDailyPrice(today.minusDays(10), BigDecimal("100.0")),
        createDailyPrice(today, BigDecimal("100.0")),
      )
    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(stablePrices)

    val result = calculationService.calculateRollingXirr(instrumentCode)

    assertThat(result).isNotEmpty()

    val transactions = result[0].getTransactions().sortedBy { it.date }
    assertThat(transactions).hasSize(2)
    assertThat(transactions[0].amount).isEqualTo(-1000.0)
    assertThat(transactions[1].amount).isEqualTo(1000.0)
  }

  private fun createDailyPrice(
    date: LocalDate,
    price: BigDecimal,
  ): DailyPrice =
    DailyPrice(
      instrument = testInstrument,
      entryDate = date,
      providerName = ProviderName.ALPHA_VANTAGE,
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
              assertThat(xirr.calculate()).isGreaterThan(-1.0)
              assertThat(xirr.getTransactions().last().amount).isGreaterThan(0.0)
            }
          },
        ),
        Arguments.of(
          "Very low ending price",
          listOf(Pair(30, 25.0), Pair(20, 20.0), Pair(10, 0.01)),
          { result: List<Xirr> ->
            result.forEach { xirr ->
              assertThat(xirr.calculate()).isLessThanOrEqualTo(0.99)
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
            assertThat(result).isNotEmpty()
            val xirrValue = result[0].calculate()
            assertThat(xirrValue).isGreaterThan(-1.0)
            assertThat(xirrValue).isLessThan(10.0)
          },
        ),
      )
  }

  @Test
  fun `calculateBatchXirrAsync processes single date successfully`() =
    runBlocking {
      val dates = listOf(today)
      val summary = createTestSummary(today)

      whenever(portfolioSummaryService.calculateSummaryForDate(today)).thenReturn(summary)

      val result = calculationService.calculateBatchXirrAsync(dates)

      assertThat(result.processedDates).isEqualTo(1)
      assertThat(result.failedCalculations).isEmpty()
      assertThat(result.duration).isGreaterThanOrEqualTo(0)
      verify(portfolioSummaryService).calculateSummaryForDate(today)
      verify(portfolioSummaryService).saveDailySummary(summary)
    }

  @Test
  fun `calculateBatchXirrAsync processes multiple dates successfully`() =
    runBlocking {
      val dates =
        listOf(
          today,
          today.plusDays(1),
          today.plusDays(2),
        )

      dates.forEach { date ->
        val summary = createTestSummary(date)
        whenever(portfolioSummaryService.calculateSummaryForDate(date)).thenReturn(summary)
      }

      val result = calculationService.calculateBatchXirrAsync(dates)

      assertThat(result.processedDates).isEqualTo(3)
      assertThat(result.processedInstruments).isEqualTo(0)
      assertThat(result.failedCalculations).isEmpty()
      verify(portfolioSummaryService, times(3)).calculateSummaryForDate(any())
      verify(portfolioSummaryService, times(3)).saveDailySummary(any())
    }

  @Test
  fun `calculateBatchXirrAsync handles calculation failure`() =
    runBlocking {
      val successDate = today
      val failDate = today.plusDays(1)
      val dates = listOf(successDate, failDate)

      val summary = createTestSummary(successDate)
      whenever(portfolioSummaryService.calculateSummaryForDate(successDate)).thenReturn(summary)
      whenever(portfolioSummaryService.calculateSummaryForDate(failDate)).thenThrow(
        RuntimeException("Calculation failed"),
      )

      val result = calculationService.calculateBatchXirrAsync(dates)

      assertThat(result.processedDates).isEqualTo(1)
      assertThat(result.failedCalculations).hasSize(1)
      assertThat(result.failedCalculations[0]).contains("Failed for date")
    }

  @Test
  fun `calculateBatchXirrAsync handles empty date list`() =
    runBlocking {
      val result = calculationService.calculateBatchXirrAsync(emptyList())

      assertThat(result.processedDates).isEqualTo(0)
      assertThat(result.failedCalculations).isEmpty()
      assertThat(result.duration).isGreaterThanOrEqualTo(0)
    }

  private fun createTestSummary(date: LocalDate): PortfolioDailySummary =
    PortfolioDailySummary(
      entryDate = date,
      totalValue = BigDecimal("10000"),
      xirrAnnualReturn = BigDecimal("0.15"),
      totalProfit = BigDecimal("2000"),
      earningsPerDay = BigDecimal("5.48"),
    )
}
