package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PriceChangePeriod
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.DailyPriceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class DailyPriceServiceTest {
  private val dailyPriceRepository = mockk<DailyPriceRepository>()
  private val dailyPriceService = DailyPriceService(dailyPriceRepository)

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

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusYears(10),
        testDate,
      )
    } returns dailyPrice

    val result = dailyPriceService.getPrice(testInstrument, testDate)

    expect(result).toEqualNumerically(BigDecimal("150.50"))
  }

  @Test
  fun `should getPrice throws NoSuchElementException when no price found`() {
    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusYears(10),
        testDate,
      )
    } returns null

    val exception =
      org.junit.jupiter.api.assertThrows<NoSuchElementException> {
        dailyPriceService.getPrice(testInstrument, testDate)
      }

    expect(exception.message!!).toContain("No price found for AAPL on or before $testDate")
  }

  @Test
  fun `should saveDailyPrice saves new price when no existing price found`() {
    val newDailyPrice = createDailyPrice(closePrice = BigDecimal("100.00"))

    every {
      dailyPriceRepository.findByInstrumentAndEntryDateAndProviderName(
        testInstrument,
        testDate,
        ProviderName.ALPHA_VANTAGE,
      )
    } returns null
    every { dailyPriceRepository.save(newDailyPrice) } returns newDailyPrice

    val result = dailyPriceService.saveDailyPrice(newDailyPrice)

    expect(result).toEqual(newDailyPrice)
    verify { dailyPriceRepository.save(newDailyPrice) }
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

    every {
      dailyPriceRepository.findByInstrumentAndEntryDateAndProviderName(
        testInstrument,
        testDate,
        ProviderName.ALPHA_VANTAGE,
      )
    } returns existingPrice
    every { dailyPriceRepository.save(existingPrice) } returns existingPrice

    val result = dailyPriceService.saveDailyPrice(newPriceData)

    expect(result.closePrice).toEqualNumerically(BigDecimal("110.00"))
    expect(result.openPrice).notToEqualNull().toEqualNumerically(BigDecimal("105.00"))
    expect(result.highPrice).notToEqualNull().toEqualNumerically(BigDecimal("115.00"))
    expect(result.lowPrice).notToEqualNull().toEqualNumerically(BigDecimal("104.00"))
    expect(result.volume).toEqual(1500000L)
    verify { dailyPriceRepository.save(existingPrice) }
  }

  @Test
  fun `should getLastPriceChange returns null when recentPrices is empty`() {
    every {
      dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument)
    } returns emptyList()

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

    every {
      dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument)
    } returns prices

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

    every {
      dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument)
    } returns prices

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("10.00"))
    expect(result.changePercent).toEqual(10.0)
  }

  @Test
  fun `should getLastPriceChange returns PriceChange with negative change`() {
    val prices =
      listOf(
        createDailyPrice(closePrice = BigDecimal("90.00"), date = testDate),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1)),
      )

    every {
      dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument)
    } returns prices

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("-10.00"))
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

    every {
      dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument)
    } returns prices

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

    every {
      dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument)
    } returns prices

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("10.00"))
    expect(result.changePercent).toEqual(10.0)
  }

  @Test
  fun `should calculateChangePercent with positive change returns correct percentage`() {
    val prices =
      listOf(
        createDailyPrice(closePrice = BigDecimal("150.00"), date = testDate),
        createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1)),
      )

    every {
      dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument)
    } returns prices

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

    every {
      dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument)
    } returns prices

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

    every {
      dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(testInstrument)
    } returns prices

    val result = dailyPriceService.getLastPriceChange(testInstrument)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("3.50"))
    expect(result.changePercent).toEqual(3.5)
  }

  @Test
  fun `should findLastDailyPrice returns most recent price within date range`() {
    val dailyPrice = createDailyPrice(closePrice = BigDecimal("120.00"), date = testDate.minusDays(5))

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusYears(10),
        testDate,
      )
    } returns dailyPrice

    val result = dailyPriceService.findLastDailyPrice(testInstrument, testDate)

    expect(result).toEqual(dailyPrice)
  }

  @Test
  fun `should findLastDailyPrice returns null when no price found in range`() {
    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusYears(10),
        testDate,
      )
    } returns null

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

    every { dailyPriceRepository.findAllByInstrument(testInstrument) } returns prices

    val result = dailyPriceService.findAllByInstrument(testInstrument)

    expect(result).toHaveSize(3)
    expect(result).toEqual(prices)
  }

  @Test
  fun `should getPriceChange with 24h period returns correct change`() {
    val currentDate = LocalDate.now()
    val currentPrice = createDailyPrice(closePrice = BigDecimal("110.00"), date = currentDate)
    val yesterdayPrice = createDailyPrice(closePrice = BigDecimal("100.00"), date = currentDate.minusDays(1))

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        currentDate.minusYears(10),
        currentDate,
      )
    } returns currentPrice

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        currentDate.minusDays(6),
        currentDate.minusDays(1),
      )
    } returns yesterdayPrice

    val result = dailyPriceService.getPriceChange(testInstrument, PriceChangePeriod.P24H)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("10.00"))
    expect(result.changePercent).toEqual(10.0)
  }

  @Test
  fun `should getPriceChange with 7d period returns correct change`() {
    val currentDate = LocalDate.now()
    val currentPrice = createDailyPrice(closePrice = BigDecimal("120.00"), date = currentDate)
    val weekAgoPrice = createDailyPrice(closePrice = BigDecimal("100.00"), date = currentDate.minusDays(7))

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        currentDate.minusYears(10),
        currentDate,
      )
    } returns currentPrice

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        currentDate.minusDays(12),
        currentDate.minusDays(7),
      )
    } returns weekAgoPrice

    val result = dailyPriceService.getPriceChange(testInstrument, PriceChangePeriod.P7D)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("20.00"))
    expect(result.changePercent).toEqual(20.0)
  }

  @Test
  fun `should getPriceChange with 30d period returns correct change`() {
    val currentDate = LocalDate.now()
    val currentPrice = createDailyPrice(closePrice = BigDecimal("150.00"), date = currentDate)
    val monthAgoPrice = createDailyPrice(closePrice = BigDecimal("100.00"), date = currentDate.minusDays(30))

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        currentDate.minusYears(10),
        currentDate,
      )
    } returns currentPrice

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        currentDate.minusDays(35),
        currentDate.minusDays(30),
      )
    } returns monthAgoPrice

    val result = dailyPriceService.getPriceChange(testInstrument, PriceChangePeriod.P30D)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("50.00"))
    expect(result.changePercent).toEqual(50.0)
  }

  @Test
  fun `should getPriceChange returns null when current price not found`() {
    val currentDate = LocalDate.now()

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        currentDate.minusYears(10),
        currentDate,
      )
    } returns null

    val result = dailyPriceService.getPriceChange(testInstrument, PriceChangePeriod.P24H)

    expect(result).toEqual(null)
  }

  @Test
  fun `should getPriceChange returns null when historical price not found`() {
    val currentDate = LocalDate.now()
    val currentPrice = createDailyPrice(closePrice = BigDecimal("110.00"), date = currentDate)

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        currentDate.minusYears(10),
        currentDate,
      )
    } returns currentPrice

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        currentDate.minusDays(6),
        currentDate.minusDays(1),
      )
    } returns null

    val result = dailyPriceService.getPriceChange(testInstrument, PriceChangePeriod.P24H)

    expect(result).toEqual(null)
  }

  @Test
  fun `should getPriceChange with negative change returns correct percentage`() {
    val currentDate = LocalDate.now()
    val currentPrice = createDailyPrice(closePrice = BigDecimal("80.00"), date = currentDate)
    val yesterdayPrice = createDailyPrice(closePrice = BigDecimal("100.00"), date = currentDate.minusDays(1))

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        currentDate.minusYears(10),
        currentDate,
      )
    } returns currentPrice

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        currentDate.minusDays(6),
        currentDate.minusDays(1),
      )
    } returns yesterdayPrice

    val result = dailyPriceService.getPriceChange(testInstrument, PriceChangePeriod.P24H)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("-20.00"))
    expect(result.changePercent).toEqual(-20.0)
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
