package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.service.xirr.Xirr
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.stream.Stream
import kotlin.math.abs

@ExtendWith(MockitoExtension::class)
class CalculatorServiceTest {

  @Mock
  private lateinit var dataRetrievalService: DailyPriceService

  @Mock
  private lateinit var instrumentService: InstrumentService

  @Mock
  private lateinit var portfolioSummaryService: PortfolioSummaryService

  @Mock
  private lateinit var clock: Clock

  private lateinit var calculatorService: CalculatorService
  private lateinit var testInstrument: Instrument
  private lateinit var testDispatcher: CoroutineDispatcher
  private val TODAY = LocalDate.of(2024, 5, 15)
  private val INSTRUMENT_CODE = "QDVE:GER:EUR"

  @BeforeEach
  fun setUp() {
    testDispatcher = Dispatchers.Unconfined
    testInstrument = createTestInstrument()
    lenient().whenever(clock.instant()).thenReturn(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant())
    lenient().whenever(clock.zone).thenReturn(ZoneId.systemDefault())

    calculatorService = CalculatorService(
      dataRetrievalService = dataRetrievalService,
      instrumentService = instrumentService,
      calculationDispatcher = testDispatcher,
      portfolioSummaryService = portfolioSummaryService,
      clock = clock
    )

    whenever(instrumentService.getInstrument(INSTRUMENT_CODE)).thenReturn(testInstrument)
  }

  private fun createTestInstrument() = Instrument(
    symbol = INSTRUMENT_CODE,
    name = "iShares S&P 500 Information Technology Sector",
    category = "ETF",
    baseCurrency = "EUR",
    providerName = ProviderName.ALPHA_VANTAGE
  ).apply {
    id = 1L
    currentPrice = BigDecimal("28.50")
  }

  @Test
  fun `returns empty list when no daily prices exist`() {
    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(emptyList())

    val result = calculatorService.calculateRollingXirr(INSTRUMENT_CODE)

    assertThat(result).isEmpty()
  }

  @Test
  fun `returns empty list when only one daily price exists`() {
    val singlePrice = createDailyPrice(TODAY.minusDays(10), BigDecimal("25.0"))
    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(listOf(singlePrice))

    val result = calculatorService.calculateRollingXirr(INSTRUMENT_CODE)

    assertThat(result).isEmpty()
  }

  @ParameterizedTest(name = "Price trend: {0}")
  @MethodSource("providePriceTrends")
  fun `calculates appropriate XIRR values for different price trends`(
    trendDescription: String,
    startPrice: Double,
    endPrice: Double,
    expectedXirrCondition: (Double) -> Boolean
  ) {
    val prices = createPriceHistory(
      startDate = TODAY.minusDays(60),
      startPrice = startPrice,
      endPrice = endPrice,
      ascending = endPrice > startPrice
    )
    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(prices)

    val result = calculatorService.calculateRollingXirr(INSTRUMENT_CODE)

    assertThat(result).isNotEmpty()
    assertThat(result[0].getTransactions().size).isGreaterThanOrEqualTo(2)
    assertThat(result[0].getTransactions().last().amount).isGreaterThan(0.0)

    val xirrValue = result[0].calculate()
    assertThat(expectedXirrCondition(xirrValue)).isTrue()
  }

  @Test
  fun `creates multiple time windows at two-week intervals`() {
    val prices = createPriceHistory(
      startDate = TODAY.minusMonths(6),
      startPrice = 20.0,
      endPrice = 35.0
    )
    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(prices)

    val result = calculatorService.calculateRollingXirr(INSTRUMENT_CODE)

    assertThat(result).isNotEmpty()

    val endDates = result.map { it.getTransactions().maxOf { tx -> tx.date } }
      .distinct().sorted()

    assertThat(endDates.size).isGreaterThan(1)
    assertThat(endDates[1].toEpochDay() - endDates[0].toEpochDay()).isEqualTo(15)
  }

  @ParameterizedTest(name = "Price scenario: {0}")
  @MethodSource("providePriceScenarios")
  fun `handles various price scenarios appropriately`(
    scenarioName: String,
    prices: List<Pair<Int, Double>>,
    validator: (List<Xirr>) -> Unit
  ) {
    val dailyPrices = prices.map { (daysAgo, price) ->
      createDailyPrice(TODAY.minusDays(daysAgo.toLong()), BigDecimal(price.toString()))
    }

    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(dailyPrices)

    val result = calculatorService.calculateRollingXirr(INSTRUMENT_CODE)

    validator(result)
  }

  @Test
  fun `correctly accumulates investment over multiple price points`() {
    val stablePrices = listOf(
      createDailyPrice(TODAY.minusDays(30), BigDecimal("100.0")),
      createDailyPrice(TODAY.minusDays(20), BigDecimal("100.0")),
      createDailyPrice(TODAY.minusDays(10), BigDecimal("100.0"))
    )
    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(stablePrices)

    val result = calculatorService.calculateRollingXirr(INSTRUMENT_CODE)

    assertThat(result).isNotEmpty()

    val transactions = result[0].getTransactions().sortedBy { it.date }
    assertThat(transactions).hasSize(4)
    assertThat(transactions[0].amount).isEqualTo(-1000.0)
    assertThat(transactions[1].amount).isEqualTo(-1000.0)
    assertThat(transactions[2].amount).isEqualTo(-1000.0)
    assertThat(transactions[3].amount).isEqualTo(3000.0)
  }

  private fun createDailyPrice(date: LocalDate, price: BigDecimal): DailyPrice {
    return DailyPrice(
      instrument = testInstrument,
      entryDate = date,
      providerName = ProviderName.ALPHA_VANTAGE,
      openPrice = price,
      highPrice = price,
      lowPrice = price,
      closePrice = price,
      volume = 1000L
    )
  }

  private fun createPriceHistory(
    startDate: LocalDate,
    startPrice: Double,
    endPrice: Double,
    ascending: Boolean = true
  ): List<DailyPrice> {
    val dates = listOf(
      startDate,
      startDate.plusDays(15),
      startDate.plusDays(30),
      startDate.plusDays(45),
      TODAY
    )

    return dates.mapIndexed { index, date ->
      val ratio = index / 4.0
      val price = if (ascending) {
        startPrice + (endPrice - startPrice) * ratio
      } else {
        endPrice + (startPrice - endPrice) * (1 - ratio)
      }
      createDailyPrice(date, BigDecimal(price))
    }
  }

  companion object {
    @JvmStatic
    fun providePriceTrends(): Stream<Arguments> = Stream.of(
      Arguments.of(
        "Upward trend",
        25.0,
        30.0,
        { xirr: Double -> xirr > 0 }
      ),
      Arguments.of(
        "Downward trend",
        30.0,
        20.0,
        { xirr: Double -> xirr < 0 && xirr > -1.0 }
      ),
      Arguments.of(
        "Stable prices",
        100.0,
        100.0,
        { xirr: Double -> abs(xirr) < 0.0001 }
      )
    )

    @JvmStatic
    fun providePriceScenarios(): Stream<Arguments> = Stream.of(
      Arguments.of(
        "Extreme high price",
        listOf(Pair(30, 25.0), Pair(20, 1000000.0)),
        { result: List<Xirr> ->
          result.forEach { xirr ->
            assertThat(xirr.calculate()).isGreaterThan(-1.0)
            assertThat(xirr.getTransactions().last().amount).isGreaterThan(0.0)
          }
        }
      ),
      Arguments.of(
        "Very low ending price",
        listOf(Pair(30, 25.0), Pair(20, 20.0), Pair(10, 0.01)),
        { result: List<Xirr> ->
          result.forEach { xirr ->
            assertThat(xirr.calculate()).isLessThanOrEqualTo(0.99)
          }
        }
      ),
      Arguments.of(
        "Market-like fluctuations",
        listOf(
          Pair(50, 98.50),
          Pair(40, 102.30),
          Pair(30, 99.75),
          Pair(20, 105.40),
          Pair(10, 103.80)
        ),
        { result: List<Xirr> ->
          assertThat(result).isNotEmpty()
          assertThat(result[0].calculate()).isBetween(-0.5, 0.5)
        }
      )
    )
  }
}
