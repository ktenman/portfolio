package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.mockStatic
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class CalculatorServiceTest {

  @Mock
  private lateinit var dataRetrievalService: DailyPriceService

  @Mock
  private lateinit var instrumentService: InstrumentService

  @Mock
  private lateinit var portfolioSummaryService: PortfolioSummaryService

  private lateinit var calculatorService: CalculatorService
  private lateinit var testInstrument: Instrument
  private lateinit var testDispatcher: CoroutineDispatcher
  private val TODAY = LocalDate.of(2024, 5, 15)
  private val INSTRUMENT_CODE = "QDVE:GER:EUR"

  @BeforeEach
  fun setUp() {
    testDispatcher = Dispatchers.Unconfined
    testInstrument = createTestInstrument()
    calculatorService = CalculatorService(
      dataRetrievalService = dataRetrievalService,
      instrumentService = instrumentService,
      calculationDispatcher = testDispatcher,
      portfolioSummaryService = portfolioSummaryService
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

  @Test
  fun `calculates accurate XIRR values for upward trending price history`() {
    val prices = createPriceHistory(startDate = TODAY.minusDays(60), startPrice = 25.0, endPrice = 30.0)
    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(prices)
    mockTodayDate(TODAY)

    val result = calculatorService.calculateRollingXirr(INSTRUMENT_CODE)

    assertThat(result).isNotEmpty()
    assertThat(result.size).isGreaterThan(1)
    assertThat(result[0].getTransactions().size).isGreaterThanOrEqualTo(2)
    assertThat(result[0].getTransactions().last().amount).isGreaterThan(0.0)
  }

  @Test
  fun `excludes extreme negative XIRR values from results`() {
    val prices = createPriceHistory(
      startDate = TODAY.minusDays(60),
      startPrice = 30.0,
      endPrice = 20.0,
      ascending = false
    )
    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(prices)
    mockTodayDate(TODAY)

    val result = calculatorService.calculateRollingXirr(INSTRUMENT_CODE)

    result.forEach { xirr ->
      assertThat(xirr.calculate()).isGreaterThan(-1.0)
    }
  }

  @Test
  fun `handles error conditions gracefully`() {
    val prices = createPriceHistory(startDate = TODAY.minusDays(60), startPrice = 25.0, endPrice = 30.0)
    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(prices)
    mockTodayDate(TODAY)

    val result = calculatorService.calculateRollingXirr(INSTRUMENT_CODE)

    assertThat(result).isNotNull
  }

  @Test
  fun `creates multiple time windows at two-week intervals`() {
    val prices = createPriceHistory(
      startDate = TODAY.minusMonths(6),
      startPrice = 20.0,
      endPrice = 35.0
    )
    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(prices)
    mockTodayDate(TODAY)

    val result = calculatorService.calculateRollingXirr(INSTRUMENT_CODE)

    assertThat(result).isNotEmpty()

    val endDates = result.map { it.getTransactions().maxOf { tx -> tx.date } }
      .distinct().sorted()

    assertThat(endDates.size).isGreaterThan(1)
    assertThat(endDates[1].toEpochDay() - endDates[0].toEpochDay()).isEqualTo(15)
  }

  @Test
  fun `handles extreme prices correctly`() {
    val prices = listOf(
      createDailyPrice(TODAY.minusDays(30), BigDecimal("25.0")),
      createDailyPrice(TODAY.minusDays(20), BigDecimal("1000000.0"))
    )
    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(prices)
    mockTodayDate(TODAY)

    val result = calculatorService.calculateRollingXirr(INSTRUMENT_CODE)

    result.forEach { xirr ->
      val transactions = xirr.getTransactions()
      assertThat(xirr.calculate()).isGreaterThan(-1.0)
      assertThat(transactions.last().amount).isGreaterThan(0.0)
    }
  }

  @Test
  fun `handles very low prices correctly`() {
    val prices = listOf(
      createDailyPrice(TODAY.minusDays(30), BigDecimal("25.0")),
      createDailyPrice(TODAY.minusDays(20), BigDecimal("20.0")),
      createDailyPrice(TODAY.minusDays(10), BigDecimal("0.01"))
    )
    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(prices)
    mockTodayDate(TODAY)

    val result = calculatorService.calculateRollingXirr(INSTRUMENT_CODE)

    result.forEach { xirr ->
      assertThat(xirr.calculate()).isLessThanOrEqualTo(0.9899995279312134)
    }
  }

  @Test
  fun `correctly accumulates investment over multiple price points`() {
    val stablePrices = listOf(
      createDailyPrice(TODAY.minusDays(30), BigDecimal("100.0")),
      createDailyPrice(TODAY.minusDays(20), BigDecimal("100.0")),
      createDailyPrice(TODAY.minusDays(10), BigDecimal("100.0"))
    )
    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(stablePrices)
    mockTodayDate(TODAY)

    val result = calculatorService.calculateRollingXirr(INSTRUMENT_CODE)

    assertThat(result).isNotEmpty()

    val transactions = result[0].getTransactions().sortedBy { it.date }
    assertThat(transactions).hasSize(4)
    assertThat(transactions[0].amount).isEqualTo(-1000.0)
    assertThat(transactions[1].amount).isEqualTo(-1000.0)
    assertThat(transactions[2].amount).isEqualTo(-1000.0)
    assertThat(transactions[3].amount).isEqualTo(3000.0)
  }

  @Test
  fun `calculates reasonable XIRR for market-like price fluctuations`() {
    val realisticPrices = listOf(
      createDailyPrice(TODAY.minusDays(50), BigDecimal("98.50")),
      createDailyPrice(TODAY.minusDays(40), BigDecimal("102.30")),
      createDailyPrice(TODAY.minusDays(30), BigDecimal("99.75")),
      createDailyPrice(TODAY.minusDays(20), BigDecimal("105.40")),
      createDailyPrice(TODAY.minusDays(10), BigDecimal("103.80"))
    )
    whenever(dataRetrievalService.findAllByInstrument(testInstrument)).thenReturn(realisticPrices)
    mockTodayDate(TODAY)

    val result = calculatorService.calculateRollingXirr(INSTRUMENT_CODE)

    assertThat(result).isNotEmpty()
    assertThat(result[0].calculate()).isBetween(-0.5, 0.5)
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

  private fun mockTodayDate(fixedDate: LocalDate) {
    mockStatic(LocalDate::class.java).use { mockedLocalDate ->
      mockedLocalDate.`when`<LocalDate> { LocalDate.now() }.thenReturn(fixedDate)
    }
  }
}
