package ee.tenman.portfolio.job

import ee.tenman.portfolio.common.DailyPriceData
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.ft.HistoricalPricesService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.service.pricing.PriceSnapshotService
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.createInstrument
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.scheduling.TaskScheduler
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TreeMap

class FtDataRetrievalJobTest {
  private val instrumentService = mockk<InstrumentService>()
  private val historicalPricesService = mockk<HistoricalPricesService>()
  private val dataProcessingUtil = mockk<DataProcessingUtil>(relaxed = true)
  private val jobExecutionService = mockk<JobExecutionService>(relaxed = true)
  private val priceSnapshotService = mockk<PriceSnapshotService>()
  private val taskScheduler = mockk<TaskScheduler>(relaxed = true)
  private val clock = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneId.of("UTC"))

  private val job =
    FtDataRetrievalJob(
      instrumentService,
      historicalPricesService,
      dataProcessingUtil,
      jobExecutionService,
      priceSnapshotService,
      taskScheduler,
      clock,
    )

  private lateinit var instrument: Instrument

  @BeforeEach
  fun setUp() {
    clearMocks(instrumentService, historicalPricesService, priceSnapshotService)
    instrument = createInstrument()
  }

  @Test
  fun `should save price snapshot after processing FT data`() {
    val ftData = TreeMap<LocalDate, DailyPriceData>()
    ftData[LocalDate.of(2024, 1, 14)] = testPriceData(BigDecimal("148.00"))
    ftData[LocalDate.of(2024, 1, 15)] = testPriceData(BigDecimal("150.00"))
    every { instrumentService.getInstrumentsByProvider(ProviderName.FT) } returns listOf(instrument)
    every { historicalPricesService.fetchPrices("AAPL") } returns ftData
    every { priceSnapshotService.saveSnapshot(instrument, BigDecimal("150.00"), ProviderName.FT) } just runs

    job.execute()

    verify(exactly = 1) { priceSnapshotService.saveSnapshot(instrument, BigDecimal("150.00"), ProviderName.FT) }
  }

  @Test
  fun `should not save snapshot when FT data is empty`() {
    every { instrumentService.getInstrumentsByProvider(ProviderName.FT) } returns listOf(instrument)
    every { historicalPricesService.fetchPrices("AAPL") } returns emptyMap()

    job.execute()

    verify(exactly = 0) { priceSnapshotService.saveSnapshot(any(), any(), any()) }
  }

  @Test
  fun `should continue processing when snapshot save fails`() {
    val ftData = TreeMap<LocalDate, DailyPriceData>()
    ftData[LocalDate.of(2024, 1, 15)] = testPriceData(BigDecimal("150.00"))
    every { instrumentService.getInstrumentsByProvider(ProviderName.FT) } returns listOf(instrument)
    every { historicalPricesService.fetchPrices("AAPL") } returns ftData
    every { priceSnapshotService.saveSnapshot(any(), any(), any()) } throws RuntimeException("DB error")

    job.execute()

    verify(exactly = 1) { dataProcessingUtil.processDailyData(instrument, ftData, ProviderName.FT) }
  }

  private fun testPriceData(close: BigDecimal): DailyPriceData =
    object : DailyPriceData {
      override val open = close
      override val high = close
      override val low = close
      override val close = close
      override val volume = 1000L
    }
}
