package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.common.DailyPriceDataImpl
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.ft.HistoricalPricesService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.service.pricing.DailyPriceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.scheduling.TaskScheduler
import java.math.BigDecimal
import java.time.LocalDate

class InstrumentPriceGapFillingJobTest {
  private val instrumentService: InstrumentService = mockk()
  private val dailyPriceService: DailyPriceService = mockk()
  private val ftHistoricalPricesService: HistoricalPricesService = mockk()
  private val jobExecutionService: JobExecutionService = mockk(relaxed = true)
  private val taskScheduler: TaskScheduler = mockk(relaxed = true)

  private lateinit var job: InstrumentPriceGapFillingJob

  @BeforeEach
  fun setUp() {
    job =
      InstrumentPriceGapFillingJob(
      instrumentService,
      dailyPriceService,
      ftHistoricalPricesService,
      jobExecutionService,
      taskScheduler,
    )
  }

  @Test
  fun `should skip execution when no LIGHTYEAR instruments found`() {
    every { instrumentService.getAllInstrumentsWithoutFiltering() } returns emptyList()

    job.execute()

    verify(exactly = 0) { ftHistoricalPricesService.fetchPrices(any()) }
  }

  @Test
  fun `should skip non-LIGHTYEAR instruments`() {
    val ftInstrument = createInstrument("AAPL", ProviderName.FT)
    every { instrumentService.getAllInstrumentsWithoutFiltering() } returns listOf(ftInstrument)

    job.execute()

    verify(exactly = 0) { ftHistoricalPricesService.fetchPrices(any()) }
  }

  @Test
  fun `should fill gaps for LIGHTYEAR instruments`() {
    val lightyearInstrument = createInstrument("VUAA:GER:EUR", ProviderName.LIGHTYEAR)
    val existingDates = setOf(LocalDate.of(2024, 1, 1))
    val ftData =
      mapOf(
      LocalDate.of(2024, 1, 1) to
        DailyPriceDataImpl(
        BigDecimal("100"),
          BigDecimal("105"),
          BigDecimal("99"),
          BigDecimal("104"),
          1000L,
      ),
        LocalDate.of(2024, 1, 2) to
          DailyPriceDataImpl(
        BigDecimal("104"),
            BigDecimal("110"),
            BigDecimal("103"),
            BigDecimal("108"),
            1500L,
      ),
          )
    every { instrumentService.getAllInstrumentsWithoutFiltering() } returns listOf(lightyearInstrument)
    every { dailyPriceService.findAllExistingDates(lightyearInstrument) } returns existingDates
    every { ftHistoricalPricesService.fetchPrices("VUAA:GER:EUR") } returns ftData
    every { dailyPriceService.saveDailyPriceIfNotExists(any()) } returns true

    job.execute()

    verify(exactly = 1) { dailyPriceService.saveDailyPriceIfNotExists(match { it.entryDate == LocalDate.of(2024, 1, 2) }) }
    verify(exactly = 0) { dailyPriceService.saveDailyPriceIfNotExists(match { it.entryDate == LocalDate.of(2024, 1, 1) }) }
  }

  @Test
  fun `should not save when FT returns empty data`() {
    val lightyearInstrument = createInstrument("UNKNOWN", ProviderName.LIGHTYEAR)
    every { instrumentService.getAllInstrumentsWithoutFiltering() } returns listOf(lightyearInstrument)
    every { dailyPriceService.findAllExistingDates(lightyearInstrument) } returns emptySet()
    every { ftHistoricalPricesService.fetchPrices("UNKNOWN") } returns emptyMap()

    job.execute()

    verify(exactly = 0) { dailyPriceService.saveDailyPriceIfNotExists(any()) }
  }

  @Test
  fun `should handle exception gracefully for individual instrument`() {
    val instrument1 = createInstrument("INST1", ProviderName.LIGHTYEAR)
    val instrument2 = createInstrument("INST2", ProviderName.LIGHTYEAR)
    val ftData =
      mapOf(
      LocalDate.of(2024, 1, 1) to
        DailyPriceDataImpl(
        BigDecimal("100"),
          BigDecimal("105"),
          BigDecimal("99"),
          BigDecimal("104"),
          1000L,
      ),
        )
    every { instrumentService.getAllInstrumentsWithoutFiltering() } returns listOf(instrument1, instrument2)
    every { dailyPriceService.findAllExistingDates(instrument1) } throws RuntimeException("DB error")
    every { dailyPriceService.findAllExistingDates(instrument2) } returns emptySet()
    every { ftHistoricalPricesService.fetchPrices("INST2") } returns ftData
    every { dailyPriceService.saveDailyPriceIfNotExists(any()) } returns true

    job.execute()

    verify(exactly = 1) { dailyPriceService.saveDailyPriceIfNotExists(any()) }
  }

  @Test
  fun `should have correct job name`() {
    expect(job.getName()).toEqual("InstrumentPriceGapFillingJob")
  }

  private fun createInstrument(
    symbol: String,
    providerName: ProviderName,
  ): Instrument =
    Instrument(
      symbol = symbol,
      name = "Test Instrument $symbol",
      category = "ETF",
      baseCurrency = "EUR",
      currentPrice = BigDecimal("100.00"),
      providerName = providerName,
    ).apply { id = symbol.hashCode().toLong() }
}
