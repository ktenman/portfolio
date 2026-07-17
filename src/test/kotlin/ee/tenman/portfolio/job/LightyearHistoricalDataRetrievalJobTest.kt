package ee.tenman.portfolio.job

import ee.tenman.portfolio.common.DailyPriceData
import ee.tenman.portfolio.common.DailyPriceDataImpl
import ee.tenman.portfolio.configuration.LightyearScrapingProperties
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.lightyear.LightyearHistoricalPricesService
import ee.tenman.portfolio.service.currency.CurrencyConversionService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.scheduling.TaskScheduler
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LightyearHistoricalDataRetrievalJobTest {
  private val instrumentService: InstrumentService = mockk()
  private val lightyearHistoricalPricesService: LightyearHistoricalPricesService = mockk()
  private val dataProcessingUtil: DataProcessingUtil = mockk(relaxed = true)
  private val currencyConversionService: CurrencyConversionService = mockk()
  private val jobExecutionService: JobExecutionService = mockk(relaxed = true)
  private val taskScheduler: TaskScheduler = mockk(relaxed = true)
  private val lightyearProperties: LightyearScrapingProperties = mockk()
  private val clock: Clock = Clock.fixed(Instant.parse("2026-07-17T10:00:00Z"), ZoneId.of("UTC"))

  private val job =
    LightyearHistoricalDataRetrievalJob(
      instrumentService = instrumentService,
      lightyearHistoricalPricesService = lightyearHistoricalPricesService,
      dataProcessingUtil = dataProcessingUtil,
      currencyConversionService = currencyConversionService,
      jobExecutionService = jobExecutionService,
      taskScheduler = taskScheduler,
      lightyearProperties = lightyearProperties,
      clock = clock,
    )

  private val historicalData: Map<LocalDate, DailyPriceData> =
    mapOf(
      LocalDate.of(2026, 7, 16) to
        DailyPriceDataImpl(
          open = BigDecimal("42.50"),
          high = BigDecimal("42.90"),
          low = BigDecimal("42.10"),
          close = BigDecimal("42.64"),
          volume = 1000L,
        ),
    )

  @Test
  fun `should convert prices using listing currency from symbol suffix instead of fund currency`() {
    val instrument = instrument("QDVE:GER:EUR", fundCurrency = Currency.USD)
    stubFetch(instrument, "uuid-qdve")
    every { currencyConversionService.convertDailyPricesToEur(historicalData, Currency.EUR) } returns historicalData

    job.execute()

    verify(exactly = 1) { currencyConversionService.convertDailyPricesToEur(historicalData, Currency.EUR) }
    verify(exactly = 1) { dataProcessingUtil.processDailyData(instrument, historicalData, ProviderName.LIGHTYEAR) }
  }

  @Test
  fun `should convert usd listed instrument prices to eur`() {
    val instrument = instrument("GOOGL:NSQ:USD", fundCurrency = Currency.USD)
    val converted: Map<LocalDate, DailyPriceData> =
      mapOf(
        LocalDate.of(2026, 7, 16) to
          DailyPriceDataImpl(
            open = BigDecimal("37.06"),
            high = BigDecimal("37.41"),
            low = BigDecimal("36.71"),
            close = BigDecimal("37.18"),
            volume = 1000L,
          ),
      )
    stubFetch(instrument, "uuid-googl")
    every { currencyConversionService.convertDailyPricesToEur(historicalData, Currency.USD) } returns converted

    job.execute()

    verify(exactly = 1) { dataProcessingUtil.processDailyData(instrument, converted, ProviderName.LIGHTYEAR) }
  }

  @Test
  fun `should fall back to eur when symbol suffix is not a known currency`() {
    val instrument = instrument("ÜKSUS", fundCurrency = Currency.USD)
    stubFetch(instrument, "uuid-üksus")
    every { currencyConversionService.convertDailyPricesToEur(historicalData, Currency.EUR) } returns historicalData

    job.execute()

    verify(exactly = 1) { currencyConversionService.convertDailyPricesToEur(historicalData, Currency.EUR) }
  }

  private fun instrument(
    symbol: String,
    fundCurrency: Currency?,
  ): Instrument =
    Instrument(
      symbol = symbol,
      name = "Test instrument",
      category = "ETF",
      baseCurrency = "EUR",
      providerName = ProviderName.LIGHTYEAR,
      fundCurrency = fundCurrency,
    )

  private fun stubFetch(
    instrument: Instrument,
    uuid: String,
  ) {
    every { instrumentService.getInstrumentsByProvider(ProviderName.LIGHTYEAR) } returns listOf(instrument)
    every { lightyearProperties.findUuidBySymbol(instrument.symbol) } returns uuid
    every { lightyearHistoricalPricesService.fetchHistoricalPrices(uuid) } returns historicalData
  }
}
