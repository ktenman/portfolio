package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.DailyPriceRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class DailyPriceServiceTest {
  @Mock
  private lateinit var dailyPriceRepository: DailyPriceRepository

  @InjectMocks
  private lateinit var dailyPriceService: DailyPriceService

  private lateinit var testInstrument: Instrument
  private val testDate = LocalDate.of(2024, 1, 15)

  @BeforeEach
  fun setUp() {
    testInstrument =
      Instrument(
        symbol = "AAPL",
        name = "Apple Inc.",
        category = "Stock",
        baseCurrency = "USD",
        currentPrice = BigDecimal("150.00"),
        providerName = ProviderName.ALPHA_VANTAGE,
      ).apply {
        id = 1L
      }
  }

  @Test
  fun `getPrice returns closePrice when price is found`() {
    val dailyPrice = createDailyPrice(closePrice = BigDecimal("150.50"))

    whenever(
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusYears(10),
        testDate,
      ),
    ).thenReturn(dailyPrice)

    val result = dailyPriceService.getPrice(testInstrument, testDate)

    assertThat(result).isEqualByComparingTo(BigDecimal("150.50"))
  }

  @Test
  fun `getPrice throws NoSuchElementException when no price found`() {
    whenever(
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusYears(10),
        testDate,
      ),
    ).thenReturn(null)

    assertThatThrownBy {
      dailyPriceService.getPrice(testInstrument, testDate)
    }.isInstanceOf(NoSuchElementException::class.java)
      .hasMessageContaining("No price found for AAPL on or before $testDate")
  }

  @Test
  fun `saveDailyPrice saves new price when no existing price found`() {
    val newDailyPrice = createDailyPrice(closePrice = BigDecimal("100.00"))

    whenever(
      dailyPriceRepository.findByInstrumentAndEntryDateAndProviderName(
        testInstrument,
        testDate,
        ProviderName.ALPHA_VANTAGE,
      ),
    ).thenReturn(null)
    whenever(dailyPriceRepository.save(newDailyPrice)).thenReturn(newDailyPrice)

    val result = dailyPriceService.saveDailyPrice(newDailyPrice)

    assertThat(result).isEqualTo(newDailyPrice)
    verify(dailyPriceRepository).save(newDailyPrice)
  }

  @Test
  fun `saveDailyPrice updates existing price when found`() {
    val existingPrice =
      createDailyPrice(
        closePrice = BigDecimal("100.00"),
        openPrice = BigDecimal("95.00"),
        highPrice = BigDecimal("105.00"),
        lowPrice = BigDecimal("94.00"),
        volume = 1000000L,
      )
    val newPriceData =
      createDailyPrice(
        closePrice = BigDecimal("110.00"),
        openPrice = BigDecimal("105.00"),
        highPrice = BigDecimal("115.00"),
        lowPrice = BigDecimal("104.00"),
        volume = 1500000L,
      )

    whenever(
      dailyPriceRepository.findByInstrumentAndEntryDateAndProviderName(
        testInstrument,
        testDate,
        ProviderName.ALPHA_VANTAGE,
      ),
    ).thenReturn(existingPrice)
    whenever(dailyPriceRepository.save(existingPrice)).thenReturn(existingPrice)

    val result = dailyPriceService.saveDailyPrice(newPriceData)

    assertThat(result.closePrice).isEqualByComparingTo(BigDecimal("110.00"))
    assertThat(result.openPrice).isEqualByComparingTo(BigDecimal("105.00"))
    assertThat(result.highPrice).isEqualByComparingTo(BigDecimal("115.00"))
    assertThat(result.lowPrice).isEqualByComparingTo(BigDecimal("104.00"))
    assertThat(result.volume).isEqualTo(1500000L)
    verify(dailyPriceRepository).save(existingPrice)
  }

  @Test
  fun `getLastPriceChange returns null when recentPrices is empty`() {
    whenever(dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument))
      .thenReturn(emptyList())

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    assertThat(result).isNull()
  }

  @Test
  fun `getLastPriceChange returns null when all prices are the same`() {
    val prices =
      listOf(
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1)),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(2)),
      )

    whenever(dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument))
      .thenReturn(prices)

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    assertThat(result).isNull()
  }

  @Test
  fun `getLastPriceChange returns PriceChange with positive change`() {
    val prices =
      listOf(
        createDailyPrice(closePrice = BigDecimal("110.00"), date = testDate),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1)),
      )

    whenever(dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument))
      .thenReturn(prices)

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    assertThat(result).isNotNull
    assertThat(result!!.changeAmount).isEqualByComparingTo(BigDecimal("10.00"))
    assertThat(result.changePercent).isEqualTo(10.0)
  }

  @Test
  fun `getLastPriceChange returns PriceChange with negative change`() {
    val prices =
      listOf(
        createDailyPrice(closePrice = BigDecimal("90.00"), date = testDate),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1)),
      )

    whenever(dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument))
      .thenReturn(prices)

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    assertThat(result).isNotNull
    assertThat(result!!.changeAmount).isEqualByComparingTo(BigDecimal("-10.00"))
    assertThat(result.changePercent).isEqualTo(-10.0)
  }

  @Test
  fun `getLastPriceChange returns PriceChange with zero change`() {
    val prices =
      listOf(
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1)),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(2)),
      )

    whenever(dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument))
      .thenReturn(prices)

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    assertThat(result).isNull()
  }

  @Test
  fun `getLastPriceChange skips duplicate prices and finds first different price`() {
    val prices =
      listOf(
        createDailyPrice(closePrice = BigDecimal("110.00"), date = testDate),
        createDailyPrice(closePrice = BigDecimal("110.00"), date = testDate.minusDays(1)),
        createDailyPrice(closePrice = BigDecimal("110.00"), date = testDate.minusDays(2)),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(3)),
        createDailyPrice(closePrice = BigDecimal("95.00"), date = testDate.minusDays(4)),
      )

    whenever(dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument))
      .thenReturn(prices)

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    assertThat(result).isNotNull
    assertThat(result!!.changeAmount).isEqualByComparingTo(BigDecimal("10.00"))
    assertThat(result.changePercent).isEqualTo(10.0)
  }

  @Test
  fun `calculateChangePercent with positive change returns correct percentage`() {
    val prices =
      listOf(
        createDailyPrice(closePrice = BigDecimal("150.00"), date = testDate),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1)),
      )

    whenever(dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument))
      .thenReturn(prices)

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    assertThat(result).isNotNull
    assertThat(result!!.changePercent).isEqualTo(50.0)
  }

  @Test
  fun `calculateChangePercent with negative change returns correct percentage`() {
    val prices =
      listOf(
        createDailyPrice(closePrice = BigDecimal("75.00"), date = testDate),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1)),
      )

    whenever(dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument))
      .thenReturn(prices)

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    assertThat(result).isNotNull
    assertThat(result!!.changePercent).isEqualTo(-25.0)
  }

  @Test
  fun `calculateChangePercent with fractional change calculates precise percentage`() {
    val prices =
      listOf(
        createDailyPrice(closePrice = BigDecimal("103.50"), date = testDate),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1)),
      )

    whenever(dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument))
      .thenReturn(prices)

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    assertThat(result).isNotNull
    assertThat(result!!.changeAmount).isEqualByComparingTo(BigDecimal("3.50"))
    assertThat(result.changePercent).isEqualTo(3.5)
  }

  @Test
  fun `findLastDailyPrice returns most recent price within date range`() {
    val dailyPrice = createDailyPrice(closePrice = BigDecimal("120.00"), date = testDate.minusDays(5))

    whenever(
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusYears(10),
        testDate,
      ),
    ).thenReturn(dailyPrice)

    val result = dailyPriceService.findLastDailyPrice(testInstrument, testDate)

    assertThat(result).isEqualTo(dailyPrice)
  }

  @Test
  fun `findLastDailyPrice returns null when no price found in range`() {
    whenever(
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusYears(10),
        testDate,
      ),
    ).thenReturn(null)

    val result = dailyPriceService.findLastDailyPrice(testInstrument, testDate)

    assertThat(result).isNull()
  }

  @Test
  fun `findAllByInstrument returns all prices for instrument`() {
    val prices =
      listOf(
        createDailyPrice(closePrice = BigDecimal("100.00")),
        createDailyPrice(closePrice = BigDecimal("110.00")),
        createDailyPrice(closePrice = BigDecimal("120.00")),
      )

    whenever(dailyPriceRepository.findAllByInstrument(testInstrument)).thenReturn(prices)

    val result = dailyPriceService.findAllByInstrument(testInstrument)

    assertThat(result).hasSize(3)
    assertThat(result).isEqualTo(prices)
  }

  private fun createDailyPrice(
    closePrice: BigDecimal,
    date: LocalDate = testDate,
    openPrice: BigDecimal? = null,
    highPrice: BigDecimal? = null,
    lowPrice: BigDecimal? = null,
    volume: Long? = null,
  ): DailyPrice =
    DailyPrice(
      instrument = testInstrument,
      entryDate = date,
      providerName = ProviderName.ALPHA_VANTAGE,
      openPrice = openPrice,
      highPrice = highPrice,
      lowPrice = lowPrice,
      closePrice = closePrice,
      volume = volume,
    )
}
