package ee.tenman.portfolio.scheduler

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.ft.HistoricalPricesService
import ee.tenman.portfolio.job.DataProcessingUtil
import ee.tenman.portfolio.job.FtDataRetrievalJob
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.scheduling.TaskScheduler
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch

class FtDataRetrievalJobConcurrencyTest {
  private val fixedClock = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneId.systemDefault())
  private val instrumentService = mockk<InstrumentService>()
  private val historicalPricesService = mockk<HistoricalPricesService>()
  private val dataProcessingUtil = mockk<DataProcessingUtil>()
  private val jobExecutionService = mockk<JobExecutionService>()
  private val taskScheduler = mockk<TaskScheduler>()

  private lateinit var job: FtDataRetrievalJob

  @BeforeEach
  fun setUp() {
    job =
      FtDataRetrievalJob(
        instrumentService,
        historicalPricesService,
        dataProcessingUtil,
        jobExecutionService,
        taskScheduler,
        fixedClock,
      )
  }

  @Test
  fun `should prevent concurrent execution`() {
    val instrument = createTestInstrument()

    every { instrumentService.getInstrumentsByProvider(ProviderName.FT) } returns listOf(instrument)
    every { historicalPricesService.fetchPrices(any()) } answers {
      Thread.sleep(100)
      emptyMap()
    }

    val firstCallStartedLatch = CountDownLatch(1)
    var secondCallSkipped = false

    val thread1 =
      Thread {
        firstCallStartedLatch.countDown()
        job.execute()
      }

    val thread2 =
      Thread {
        firstCallStartedLatch.await()
        job.execute()
        secondCallSkipped = true
      }

    thread1.start()
    thread2.start()

    thread1.join()
    thread2.join()

    expect(secondCallSkipped).toEqual(true)
  }

  @Test
  fun `should allow execution after previous execution completes`() {
    val instrument = createTestInstrument()

    every { instrumentService.getInstrumentsByProvider(ProviderName.FT) } returns listOf(instrument)
    every { historicalPricesService.fetchPrices(any()) } returns emptyMap()

    job.execute()
    job.execute()

    verify(exactly = 2) { instrumentService.getInstrumentsByProvider(ProviderName.FT) }
  }

  @Test
  fun `should release lock even when exception occurs`() {
    val instrument = createTestInstrument()

    every { instrumentService.getInstrumentsByProvider(ProviderName.FT) } returns listOf(instrument)
    every { historicalPricesService.fetchPrices(any()) } throws RuntimeException("Test error")
    every { dataProcessingUtil.processDailyData(any(), any(), any()) } returns Unit

    runCatching { job.execute() }

    job.execute()

    verify(exactly = 2) { instrumentService.getInstrumentsByProvider(ProviderName.FT) }
  }

  @Test
  fun `should handle empty instruments list`() {
    every { instrumentService.getInstrumentsByProvider(ProviderName.FT) } returns emptyList()

    job.execute()

    verify(exactly = 1) { instrumentService.getInstrumentsByProvider(ProviderName.FT) }
    verify(exactly = 0) { historicalPricesService.fetchPrices(any()) }
  }

  private fun createTestInstrument() =
    ee.tenman.portfolio.domain
      .Instrument(
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
