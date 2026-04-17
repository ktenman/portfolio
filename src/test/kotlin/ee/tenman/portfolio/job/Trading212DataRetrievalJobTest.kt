package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.pricing.PriceUpdateProcessor
import ee.tenman.portfolio.service.pricing.Trading212PriceUpdateService
import ee.tenman.portfolio.trading212.Trading212Service
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.scheduling.TaskScheduler
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
  private val taskScheduler = mockk<TaskScheduler>(relaxed = true)

  private val job =
    Trading212DataRetrievalJob(
      jobExecutionService = jobExecutionService,
      trading212Service = trading212Service,
      trading212PriceUpdateService = trading212PriceUpdateService,
      priceUpdateProcessor = priceUpdateProcessor,
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
  fun `execute delegates price processing to the price update processor for Trading212`() {
    job.execute()

    verify {
      priceUpdateProcessor.processPriceUpdates(
        platform = Platform.TRADING212,
        log = any(),
        fetchPrices = any(),
        processSymbol = any(),
      )
    }
  }
}
