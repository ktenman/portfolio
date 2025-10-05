package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.DailyPriceRepository
import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect
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
  fun `should getPrice returns closePrice when price is found`() {
    val dailyPrice = createDailyPrice(closePrice = BigDecimal("150.50"))

    whenever(
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusYears(10),
        testDate,
      ),
    ).thenReturn(dailyPrice)

    val result = dailyPriceService.getPrice(testInstrument, testDate)

    expect(result).toEqual(BigDecimal("150.50"))
  }

  @Test
  fun `should getPrice throws NoSuchElementException when no price found`() {
    whenever(
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusYears(10),
        testDate,
      ),
    ).thenReturn(null)

    val exception =
      org.junit.jupiter.api.assertThrows<NoSuchElementException> {
        dailyPriceService.getPrice(testInstrument, testDate)
      }

    expect(exception.message!!).toContain("No price found for AAPL on or before $testDate")
  }

  @Test
  fun `should saveDailyPrice saves new price when no existing price found`() {
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

    expect(result).toEqual(newDailyPrice)
    verify(dailyPriceRepository).save(newDailyPrice)
  }

  @Test
  fun `should saveDailyPrice updates existing price when found`() {
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

    expect(result.closePrice).toEqual(BigDecimal("110.00"))
    expect(result.openPrice).toEqual(BigDecimal("105.00"))
    expect(result.highPrice).toEqual(BigDecimal("115.00"))
    expect(result.lowPrice).toEqual(BigDecimal("104.00"))
    expect(result.volume).toEqual(1500000L)
    verify(dailyPriceRepository).save(existingPrice)
  }

  @Test
  fun `should getLastPriceChange returns null when recentPrices is empty`() {
    whenever(dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument))
      .thenReturn(emptyList())

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    expect(result).toEqual(null)
  }

  @Test
  fun `should getLastPriceChange returns null when all prices are the same`() {
    val prices =
      listOf(
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1)),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(2)),
      )

    whenever(dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument))
      .thenReturn(prices)

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    expect(result).toEqual(null)
  }

  @Test
  fun `should getLastPriceChange returns PriceChange with positive change`() {
    val prices =
      listOf(
        createDailyPrice(closePrice = BigDecimal("110.00"), date = testDate),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1)),
      )

    whenever(dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument))
      .thenReturn(prices)

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqual(BigDecimal("10.00"))
    expect(result.changePercent).toEqual(10.0)
  }

  @Test
  fun `should getLastPriceChange returns PriceChange with negative change`() {
    val prices =
      listOf(
        createDailyPrice(closePrice = BigDecimal("90.00"), date = testDate),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1)),
      )

    whenever(dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument))
      .thenReturn(prices)

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqual(BigDecimal("-10.00"))
    expect(result.changePercent).toEqual(-10.0)
  }

  @Test
  fun `should getLastPriceChange returns PriceChange with zero change`() {
    val prices =
      listOf(
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1)),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(2)),
      )

    whenever(dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument))
      .thenReturn(prices)

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    expect(result).toEqual(null)
  }

  @Test
  fun `should getLastPriceChange skips duplicate prices and finds first different price`() {
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

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqual(BigDecimal("10.00"))
    expect(result.changePercent).toEqual(10.0)
  }

  @Test
  fun `should calculateChangePercent with positive change returns correct percentage`() {
    val prices =
      listOf(
        createDailyPrice(closePrice = BigDecimal("150.00"), date = testDate),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1)),
      )

    whenever(dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument))
      .thenReturn(prices)

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    expect(result).notToEqualNull()
    expect(result!!.changePercent).toEqual(50.0)
  }

  @Test
  fun `should calculateChangePercent with negative change returns correct percentage`() {
    val prices =
      listOf(
        createDailyPrice(closePrice = BigDecimal("75.00"), date = testDate),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1)),
      )

    whenever(dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument))
      .thenReturn(prices)

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    expect(result).notToEqualNull()
    expect(result!!.changePercent).toEqual(-25.0)
  }

  @Test
  fun `should calculateChangePercent with fractional change calculates precise percentage`() {
    val prices =
      listOf(
        createDailyPrice(closePrice = BigDecimal("103.50"), date = testDate),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1)),
      )

    whenever(dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument))
      .thenReturn(prices)

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqual(BigDecimal("3.50"))
    expect(result.changePercent).toEqual(3.5)
  }

  @Test
  fun `should findLastDailyPrice returns most recent price within date range`() {
    val dailyPrice = createDailyPrice(closePrice = BigDecimal("120.00"), date = testDate.minusDays(5))

    whenever(
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusYears(10),
        testDate,
      ),
    ).thenReturn(dailyPrice)

    val result = dailyPriceService.findLastDailyPrice(testInstrument, testDate)

    expect(result).toEqual(dailyPrice)
  }

  @Test
  fun `should findLastDailyPrice returns null when no price found in range`() {
    whenever(
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusYears(10),
        testDate,
      ),
    ).thenReturn(null)

    val result = dailyPriceService.findLastDailyPrice(testInstrument, testDate)

    expect(result).toEqual(null)
  }

  @Test
  fun `should findAllByInstrument returns all prices for instrument`() {
    val prices =
      listOf(
        createDailyPrice(closePrice = BigDecimal("100.00")),
        createDailyPrice(closePrice = BigDecimal("110.00")),
        createDailyPrice(closePrice = BigDecimal("120.00")),
      )

    whenever(dailyPriceRepository.findAllByInstrument(testInstrument)).thenReturn(prices)

    val result = dailyPriceService.findAllByInstrument(testInstrument)

    expect(result).toHaveSize(3)
    expect(result).toEqual(prices)
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
