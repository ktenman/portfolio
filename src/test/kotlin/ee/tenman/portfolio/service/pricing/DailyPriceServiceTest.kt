package ee.tenman.portfolio.service.pricing

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
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DailyPriceServiceTest {
  private val dailyPriceRepository = mockk<DailyPriceRepository>()
  private val clock = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneId.of("UTC"))
  private val dailyPriceService = DailyPriceService(dailyPriceRepository, clock)

  private lateinit var testInstrument: Instrument
  private val testDate = LocalDate.of(2024, 1, 15)

  @BeforeEach
  fun setUp() {
    clearMocks(dailyPriceRepository)
    testInstrument =
      Instrument(
        symbol = "AAPL",
        name = "Apple Inc.",
        category = "Stock",
        baseCurrency = "USD",
        currentPrice = BigDecimal("150.00"),
        providerName = ProviderName.FT,
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
  fun `should saveDailyPrice calls upsert with correct parameters`() {
    val dailyPrice = createDailyPrice(
      closePrice = BigDecimal("100.00"),
      openPrice = BigDecimal("95.00"),
      highPrice = BigDecimal("105.00"),
      lowPrice = BigDecimal("94.00"),
      volume = 1000000L,
    )
    every {
      dailyPriceRepository.upsert(
        instrumentId = 1L,
        entryDate = testDate,
        providerName = "FT",
        openPrice = BigDecimal("95.00"),
        highPrice = BigDecimal("105.00"),
        lowPrice = BigDecimal("94.00"),
        closePrice = BigDecimal("100.00"),
        volume = 1000000L,
      )
    } just runs

    dailyPriceService.saveDailyPrice(dailyPrice)

    verify {
      dailyPriceRepository.upsert(
        instrumentId = 1L,
        entryDate = testDate,
        providerName = "FT",
        openPrice = BigDecimal("95.00"),
        highPrice = BigDecimal("105.00"),
        lowPrice = BigDecimal("94.00"),
        closePrice = BigDecimal("100.00"),
        volume = 1000000L,
      )
    }
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
    val currentPrice = createDailyPrice(closePrice = BigDecimal("110.00"), date = testDate)
    val yesterdayPrice = createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1))

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusYears(10),
        testDate,
      )
    } returns currentPrice

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusDays(6),
        testDate.minusDays(1),
      )
    } returns yesterdayPrice

    val result = dailyPriceService.getPriceChange(testInstrument, PriceChangePeriod.P24H)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("10.00"))
    expect(result.changePercent).toEqual(10.0)
  }

  @Test
  fun `should getPriceChange with 7d period returns correct change`() {
    val currentPrice = createDailyPrice(closePrice = BigDecimal("120.00"), date = testDate)
    val weekAgoPrice = createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(7))

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusYears(10),
        testDate,
      )
    } returns currentPrice

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusDays(12),
        testDate.minusDays(7),
      )
    } returns weekAgoPrice

    val result = dailyPriceService.getPriceChange(testInstrument, PriceChangePeriod.P7D)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("20.00"))
    expect(result.changePercent).toEqual(20.0)
  }

  @Test
  fun `should getPriceChange with 30d period returns correct change`() {
    val currentPrice = createDailyPrice(closePrice = BigDecimal("150.00"), date = testDate)
    val monthAgoPrice = createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(30))

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusYears(10),
        testDate,
      )
    } returns currentPrice

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusDays(35),
        testDate.minusDays(30),
      )
    } returns monthAgoPrice

    val result = dailyPriceService.getPriceChange(testInstrument, PriceChangePeriod.P30D)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("50.00"))
    expect(result.changePercent).toEqual(50.0)
  }

  @Test
  fun `should getPriceChange returns null when current price not found`() {
    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusYears(10),
        testDate,
      )
    } returns null

    val result = dailyPriceService.getPriceChange(testInstrument, PriceChangePeriod.P24H)

    expect(result).toEqual(null)
  }

  @Test
  fun `should getPriceChange returns null when historical price not found`() {
    val currentPrice = createDailyPrice(closePrice = BigDecimal("110.00"), date = testDate)

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusYears(10),
        testDate,
      )
    } returns currentPrice

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusDays(6),
        testDate.minusDays(1),
      )
    } returns null

    val result = dailyPriceService.getPriceChange(testInstrument, PriceChangePeriod.P24H)

    expect(result).toEqual(null)
  }

  @Test
  fun `should getPriceChange with negative change returns correct percentage`() {
    val currentPrice = createDailyPrice(closePrice = BigDecimal("80.00"), date = testDate)
    val yesterdayPrice = createDailyPrice(closePrice = BigDecimal("100.00"), date = testDate.minusDays(1))

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusYears(10),
        testDate,
      )
    } returns currentPrice

    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        testInstrument,
        testDate.minusDays(6),
        testDate.minusDays(1),
      )
    } returns yesterdayPrice

    val result = dailyPriceService.getPriceChange(testInstrument, PriceChangePeriod.P24H)

    expect(result).notToEqualNull()
    expect(result!!.changeAmount).toEqualNumerically(BigDecimal("-20.00"))
    expect(result.changePercent).toEqual(-20.0)
  }

  @Test
  fun `should findAllExistingDates returns set of dates for instrument`() {
    val expectedDates = setOf(testDate, testDate.minusDays(1), testDate.minusDays(2))
    every { dailyPriceRepository.findAllEntryDatesByInstrument(testInstrument) } returns expectedDates

    val result = dailyPriceService.findAllExistingDates(testInstrument)

    expect(result).toEqual(expectedDates)
    verify { dailyPriceRepository.findAllEntryDatesByInstrument(testInstrument) }
  }

  @Test
  fun `should findAllExistingDates returns empty set when no prices exist`() {
    every { dailyPriceRepository.findAllEntryDatesByInstrument(testInstrument) } returns emptySet()

    val result = dailyPriceService.findAllExistingDates(testInstrument)

    expect(result).toEqual(emptySet())
  }

  @Test
  fun `should saveDailyPriceIfNotExists saves and returns true when price does not exist`() {
    val newDailyPrice = createDailyPrice(closePrice = BigDecimal("100.00"))
    every { dailyPriceRepository.findByInstrumentAndEntryDate(testInstrument, testDate) } returns null
    every { dailyPriceRepository.save(newDailyPrice) } returns newDailyPrice

    val result = dailyPriceService.saveDailyPriceIfNotExists(newDailyPrice)

    expect(result).toEqual(true)
    verify { dailyPriceRepository.save(newDailyPrice) }
  }

  @Test
  fun `should saveDailyPriceIfNotExists returns false when price already exists`() {
    val existingPrice = createDailyPrice(closePrice = BigDecimal("100.00"))
    val newDailyPrice = createDailyPrice(closePrice = BigDecimal("110.00"))
    every { dailyPriceRepository.findByInstrumentAndEntryDate(testInstrument, testDate) } returns existingPrice

    val result = dailyPriceService.saveDailyPriceIfNotExists(newDailyPrice)

    expect(result).toEqual(false)
    verify(exactly = 0) { dailyPriceRepository.save(any()) }
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
      providerName = ProviderName.FT,
      openPrice = openPrice,
      highPrice = highPrice,
      lowPrice = lowPrice,
      closePrice = closePrice,
      volume = volume,
    )
}
