package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.InstrumentCategory
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.pricing.PriceUpdateProcessor
import ee.tenman.portfolio.service.pricing.Trading212PriceUpdateService
import ee.tenman.portfolio.testing.fixture.TransactionFixtures
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
        name = "BNKE:PAR:EUR",
        category = InstrumentCategory.ETF.name,
        baseCurrency = "EUR",
        providerName = ProviderName.TRADING212,
      )
    every { instrumentRepository.findByProviderName(ProviderName.TRADING212) } returns listOf(bnke)
    val eligibleSymbolsSlot = slot<() -> Map<String, BigDecimal>>()
    every {
      priceUpdateProcessor.processPriceUpdates(
        platform = Platform.TRADING212,
        log = any(),
        fetchPrices = capture(eligibleSymbolsSlot),
        processSymbol = any(),
      )
    } answers {
      eligibleSymbolsSlot.captured.invoke()
    }
    every { trading212Service.fetchCurrentPrices(setOf("BNKE:PAR:EUR")) } returns
      mapOf("BNKE:PAR:EUR" to BigDecimal("327.23"))

    job.execute()

    verify { trading212Service.fetchCurrentPrices(setOf("BNKE:PAR:EUR")) }
  }

  @Test
  fun `execute passes an empty filter when no Trading212 instruments exist`() {
    every { instrumentRepository.findByProviderName(ProviderName.TRADING212) } returns emptyList()
    val eligibleSymbolsSlot = slot<() -> Map<String, BigDecimal>>()
    every {
      priceUpdateProcessor.processPriceUpdates(
        platform = Platform.TRADING212,
        log = any(),
        fetchPrices = capture(eligibleSymbolsSlot),
        processSymbol = any(),
      )
    } answers {
      eligibleSymbolsSlot.captured.invoke()
    }
    every { trading212Service.fetchCurrentPrices(emptySet()) } returns emptyMap()

    job.execute()

    verify { trading212Service.fetchCurrentPrices(emptySet()) }
  }

}
