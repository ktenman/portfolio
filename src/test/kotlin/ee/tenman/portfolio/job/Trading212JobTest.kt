package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.Trading212ScrapingProperties
import ee.tenman.portfolio.configuration.Trading212SymbolEntry
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.InstrumentCategory
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.etf.EtfBreakdownService
import ee.tenman.portfolio.service.etf.EtfHoldingService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
import ee.tenman.portfolio.service.pricing.PriceUpdateProcessor
import ee.tenman.portfolio.service.pricing.Trading212PriceUpdateService
import ee.tenman.portfolio.testing.fixture.TransactionFixtures
import ee.tenman.portfolio.trading212.Trading212HoldingsService
import ee.tenman.portfolio.trading212.Trading212Service
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.scheduling.TaskScheduler
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class Trading212DataRetrievalJobTest {
  private val fixedInstant: Instant = Instant.parse("2026-04-17T01:30:00Z")
  private val clock: Clock = Clock.fixed(fixedInstant, ZoneId.of("Europe/Tallinn"))
  private val jobExecutionService = mockk<JobExecutionService>(relaxed = true)
  private val trading212Service = mockk<Trading212Service>(relaxed = true)
  private val trading212PriceUpdateService = mockk<Trading212PriceUpdateService>(relaxed = true)
  private val priceUpdateProcessor = mockk<PriceUpdateProcessor>(relaxed = true)
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val taskScheduler = mockk<TaskScheduler>(relaxed = true)

  private val job =
    Trading212DataRetrievalJob(
      jobExecutionService = jobExecutionService,
      trading212Service = trading212Service,
      trading212PriceUpdateService = trading212PriceUpdateService,
      priceUpdateProcessor = priceUpdateProcessor,
      instrumentRepository = instrumentRepository,
      taskScheduler = taskScheduler,
      clock = clock,
    )

  @Test
  fun `scheduleInitialRun schedules the job to run fifteen seconds after startup`() {
    val instantSlot = slot<Instant>()

    job.scheduleInitialRun()

    verify { taskScheduler.schedule(any(), capture(instantSlot)) }
    expect(instantSlot.captured).toEqual(fixedInstant.plus(Duration.ofSeconds(15)))
  }

  @Test
  fun `runJob executes the price update every minute regardless of time of day`() {
    job.runJob()

    verify { jobExecutionService.executeJob(job) }
  }

  @Test
  fun `execute asks the Trading212 service only for instruments whose provider is Trading212`() {
    val bnke =
      TransactionFixtures.createInstrument(
        symbol = "BNKE:PAR:EUR",
        category = InstrumentCategory.ETF.name,
        baseCurrency = "EUR",
        providerName = ProviderName.TRADING212,
      )
    every { instrumentRepository.findByProviderName(ProviderName.TRADING212) } returns listOf(bnke)
    invokeFetchPricesWhenProcessed()
    every { trading212Service.fetchCurrentPrices(setOf("BNKE:PAR:EUR")) } returns
      mapOf("BNKE:PAR:EUR" to BigDecimal("327.23"))

    job.execute()

    verify { trading212Service.fetchCurrentPrices(setOf("BNKE:PAR:EUR")) }
  }

  @Test
  fun `execute passes an empty filter when no Trading212 instruments exist`() {
    every { instrumentRepository.findByProviderName(ProviderName.TRADING212) } returns emptyList()
    invokeFetchPricesWhenProcessed()
    every { trading212Service.fetchCurrentPrices(emptySet()) } returns emptyMap()

    job.execute()

    verify { trading212Service.fetchCurrentPrices(emptySet()) }
  }

  private fun invokeFetchPricesWhenProcessed() {
    val fetchPricesSlot = slot<() -> Map<String, BigDecimal>>()
    every {
      priceUpdateProcessor.processPriceUpdates(
        platform = Platform.TRADING212,
        log = any(),
        fetchPrices = capture(fetchPricesSlot),
        processSymbol = any(),
      )
    } answers {
      fetchPricesSlot.captured.invoke()
    }
  }
}

class Trading212HoldingsRetrievalJobTest {
  private val holdingsService = mockk<Trading212HoldingsService>()
  private val etfHoldingService = mockk<EtfHoldingService>(relaxed = true)
  private val etfBreakdownService = mockk<EtfBreakdownService>(relaxed = true)
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val jobTransactionService = mockk<JobTransactionService>(relaxed = true)
  private val scrapingProperties =
    Trading212ScrapingProperties().apply {
      symbols.add(Trading212SymbolEntry(symbol = "BNKE:PAR:EUR", ticker = "BNKEp_EQ"))
    }
  private val clock = Clock.fixed(Instant.parse("2026-04-16T23:40:00Z"), ZoneId.of("UTC"))
  private val job =
    Trading212HoldingsRetrievalJob(
      jobTransactionService = jobTransactionService,
      scrapingProperties = scrapingProperties,
      holdingsService = holdingsService,
      etfHoldingService = etfHoldingService,
      etfBreakdownService = etfBreakdownService,
      instrumentRepository = instrumentRepository,
      clock = clock,
    )

  @Test
  fun `should fetch and save holdings for each configured symbol`() {
    val holdings =
      listOf(
        HoldingData(
          name = "Banco Santander",
          ticker = "SANe_EQ",
          sector = null,
          weight = BigDecimal("13.94"),
          rank = 1,
          logoUrl = null,
        ),
      )
    every { instrumentRepository.findByProviderName(ProviderName.TRADING212) } returns
      listOf(createInstrument("BNKE:PAR:EUR"))
    every { etfHoldingService.hasHoldingsForDate("BNKE:PAR:EUR", LocalDate.of(2026, 4, 16)) } returns false
    every { holdingsService.fetchHoldings("BNKEp_EQ") } returns holdings

    job.runJob()

    verify { etfHoldingService.saveHoldings("BNKE:PAR:EUR", LocalDate.of(2026, 4, 16), holdings) }
    verify { etfBreakdownService.evictBreakdownCache() }
  }

  @Test
  fun `should skip symbols already processed for today`() {
    every { instrumentRepository.findByProviderName(ProviderName.TRADING212) } returns
      listOf(createInstrument("BNKE:PAR:EUR"))
    every { etfHoldingService.hasHoldingsForDate("BNKE:PAR:EUR", LocalDate.of(2026, 4, 16)) } returns true

    job.runJob()

    verify(exactly = 0) { holdingsService.fetchHoldings(any()) }
    verify(exactly = 0) { etfHoldingService.saveHoldings(any(), any(), any()) }
  }

  @Test
  fun `cannot fail entire job when one symbol throws`() {
    scrapingProperties.symbols.add(Trading212SymbolEntry(symbol = "OTHER:X:EUR", ticker = "OTHERp_EQ"))
    every { instrumentRepository.findByProviderName(ProviderName.TRADING212) } returns
      listOf(
        createInstrument("BNKE:PAR:EUR"),
        createInstrument("OTHER:X:EUR"),
      )
    every { etfHoldingService.hasHoldingsForDate(any(), any()) } returns false
    every { holdingsService.fetchHoldings("BNKEp_EQ") } throws RuntimeException("network")
    every { holdingsService.fetchHoldings("OTHERp_EQ") } returns emptyList()

    job.runJob()

    verify { holdingsService.fetchHoldings("OTHERp_EQ") }
  }

  @Test
  fun `should skip symbols with empty holdings without writing`() {
    every { instrumentRepository.findByProviderName(ProviderName.TRADING212) } returns
      listOf(createInstrument("BNKE:PAR:EUR"))
    every { etfHoldingService.hasHoldingsForDate("BNKE:PAR:EUR", LocalDate.of(2026, 4, 16)) } returns false
    every { holdingsService.fetchHoldings("BNKEp_EQ") } returns emptyList()

    job.runJob()

    verify(exactly = 0) { etfHoldingService.saveHoldings(any(), any(), any()) }
  }

  @Test
  fun `should skip symbols whose instrument does not use TRADING212 provider`() {
    scrapingProperties.symbols.add(Trading212SymbolEntry(symbol = "VUAA:LON:EUR", ticker = "VUAAl_EQ"))
    every { instrumentRepository.findByProviderName(ProviderName.TRADING212) } returns
      listOf(createInstrument("BNKE:PAR:EUR"))
    every { etfHoldingService.hasHoldingsForDate("BNKE:PAR:EUR", LocalDate.of(2026, 4, 16)) } returns false
    every { holdingsService.fetchHoldings("BNKEp_EQ") } returns emptyList()

    job.runJob()

    verify(exactly = 0) { holdingsService.fetchHoldings("VUAAl_EQ") }
  }

  private fun createInstrument(symbol: String): Instrument =
    Instrument(
      symbol = symbol,
      name = "Test",
      category = "ETF",
      baseCurrency = "EUR",
      providerName = ProviderName.TRADING212,
    )
}
