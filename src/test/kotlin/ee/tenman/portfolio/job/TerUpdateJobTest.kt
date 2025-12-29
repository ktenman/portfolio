package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class TerUpdateJobTest {
  private val jobTransactionService = mockk<JobTransactionService>()
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val lightyearPriceService = mockk<LightyearPriceService>()
  private val instrumentService = mockk<InstrumentService>()
  private val clock = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneId.of("UTC"))
  private lateinit var job: TerUpdateJob

  @BeforeEach
  fun setUp() {
    every { jobTransactionService.saveJobExecution(any(), any(), any(), any(), any()) } returns mockk()
    job = TerUpdateJob(jobTransactionService, instrumentRepository, lightyearPriceService, instrumentService, clock)
  }

  @Test
  fun `should update TER for all lightyear instruments`() {
    val instrument1 = createInstrument(1L, "VUAA")
    val instrument2 = createInstrument(2L, "VWCE")
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(instrument1, instrument2)
    every { lightyearPriceService.fetchFundInfo("VUAA") } returns BigDecimal("0.07")
    every { lightyearPriceService.fetchFundInfo("VWCE") } returns BigDecimal("0.22")
    every { instrumentService.updateTer(any(), any()) } just runs

    job.execute()

    verify { instrumentService.updateTer(1L, BigDecimal("0.07")) }
    verify { instrumentService.updateTer(2L, BigDecimal("0.22")) }
  }

  @Test
  fun `should skip instruments with no TER data`() {
    val instrument1 = createInstrument(1L, "VUAA")
    val instrument2 = createInstrument(2L, "BTCEUR")
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(instrument1, instrument2)
    every { lightyearPriceService.fetchFundInfo("VUAA") } returns BigDecimal("0.07")
    every { lightyearPriceService.fetchFundInfo("BTCEUR") } returns null
    every { instrumentService.updateTer(any(), any()) } just runs

    job.execute()

    verify { instrumentService.updateTer(1L, BigDecimal("0.07")) }
    verify(exactly = 0) { instrumentService.updateTer(2L, any()) }
  }

  @Test
  fun `should handle empty instrument list`() {
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns emptyList()

    job.execute()

    verify(exactly = 0) { lightyearPriceService.fetchFundInfo(any()) }
    verify(exactly = 0) { instrumentService.updateTer(any(), any()) }
  }

  @Test
  fun `should save job execution with success status`() {
    val statusSlot = slot<JobStatus>()
    val messageSlot = slot<String>()
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns emptyList()
    every { jobTransactionService.saveJobExecution(any(), any(), any(), capture(statusSlot), capture(messageSlot)) } returns mockk()

    job.runJob()

    expect(statusSlot.captured).toEqual(JobStatus.SUCCESS)
    expect(messageSlot.captured).toContain("Updated 0/0 TERs")
  }

  @Test
  fun `should save job execution with failure status on exception`() {
    val statusSlot = slot<JobStatus>()
    val messageSlot = slot<String>()
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } throws RuntimeException("Database error")
    every { jobTransactionService.saveJobExecution(any(), any(), any(), capture(statusSlot), capture(messageSlot)) } returns mockk()

    job.runJob()

    expect(statusSlot.captured).toEqual(JobStatus.FAILURE)
    expect(messageSlot.captured).toContain("Database error")
  }

  private fun createInstrument(
    id: Long,
    symbol: String,
  ): Instrument {
    val instrument =
      Instrument(
        symbol = symbol,
        name = "Test Instrument",
        category = "ETF",
        baseCurrency = "EUR",
        providerName = ProviderName.LIGHTYEAR,
      )
    instrument.id = id
    return instrument
  }
}
