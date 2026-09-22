package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.service.etf.EtfBreakdownService
import ee.tenman.portfolio.service.etf.EtfHoldingService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.vanguard.VanguardFundSnapshot
import ee.tenman.portfolio.vanguard.VanguardHoldingsService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.support.CronExpression
import java.math.BigDecimal
import java.time.LocalDate

class VanguardHoldingsRetrievalJobTest {
  private val vanguardHoldingsService: VanguardHoldingsService = mockk()
  private val etfHoldingService: EtfHoldingService = mockk(relaxed = true)
  private val etfBreakdownService: EtfBreakdownService = mockk(relaxed = true)
  private val etfHoldingsClassificationJob: EtfHoldingsClassificationJob = mockk(relaxed = true)
  private val jobExecutionService: JobExecutionService = mockk(relaxed = true)
  private val etfPositionRepository: EtfPositionRepository = mockk()

  private val job =
    VanguardHoldingsRetrievalJob(
      vanguardHoldingsService = vanguardHoldingsService,
      etfHoldingService = etfHoldingService,
      etfBreakdownService = etfBreakdownService,
      etfHoldingsClassificationJob = etfHoldingsClassificationJob,
      jobExecutionService = jobExecutionService,
      etfPositionRepository = etfPositionRepository,
    )

  @BeforeEach
  fun setup() {
    every { etfHoldingService.hasHoldingsForDate(any(), any()) } returns false
  }

  @Test
  fun `should skip the startup import when both funds already have positions`() {
    every { etfPositionRepository.existsByEtfInstrumentSymbol(any()) } returns true

    job.runStartupImport()

    verify(exactly = 0) { jobExecutionService.executeJob(any()) }
  }

  @Test
  fun `should classify sectors right after the startup import`() {
    every { etfPositionRepository.existsByEtfInstrumentSymbol(any()) } returns false

    job.runStartupImport()

    verifyOrder {
      jobExecutionService.executeJob(job)
      jobExecutionService.executeJob(etfHoldingsClassificationJob)
    }
  }

  @Test
  fun `should import on startup when only one fund has positions`() {
    every { etfPositionRepository.existsByEtfInstrumentSymbol(VGLA) } returns true
    every { etfPositionRepository.existsByEtfInstrumentSymbol(VXUS) } returns false

    job.runStartupImport()

    verify(exactly = 1) { jobExecutionService.executeJob(job) }
  }

  @Test
  fun `should classify sectors even when the startup import fails`() {
    every { etfPositionRepository.existsByEtfInstrumentSymbol(any()) } returns false
    every { jobExecutionService.executeJob(job) } throws IllegalStateException("Vanguard fund E161 returned no equity holdings")

    job.runStartupImport()

    verify(exactly = 1) { jobExecutionService.executeJob(etfHoldingsClassificationJob) }
  }

  @Test
  fun `should run the nightly import through the job execution service`() {
    job.runNightlyImport()

    verify(exactly = 1) { jobExecutionService.executeJob(job) }
  }

  @Test
  fun `cannot classify sectors after the nightly import`() {
    job.runNightlyImport()

    verify(exactly = 0) { jobExecutionService.executeJob(etfHoldingsClassificationJob) }
  }

  @Test
  fun `should save each fund under the effective date reported by Vanguard`() {
    stubFund(VGLA_PORT_ID, LocalDate.of(2026, 8, 31))
    stubFund(VXUS_PORT_ID, LocalDate.of(2026, 7, 31))

    job.execute()

    verify {
      etfHoldingService.saveHoldings(VGLA, LocalDate.of(2026, 8, 31), any())
      etfHoldingService.saveHoldings(VXUS, LocalDate.of(2026, 7, 31), any())
    }
  }

  @Test
  fun `should skip a fund whose snapshot is already stored`() {
    stubFund(VGLA_PORT_ID, EFFECTIVE_DATE)
    stubFund(VXUS_PORT_ID, EFFECTIVE_DATE)
    every { etfHoldingService.hasHoldingsForDate(VGLA, EFFECTIVE_DATE) } returns true

    job.execute()

    verify(exactly = 0) { etfHoldingService.saveHoldings(VGLA, any(), any()) }
  }

  @Test
  fun `should evict the breakdown cache when a fund was saved`() {
    stubFund(VGLA_PORT_ID, EFFECTIVE_DATE)
    stubFund(VXUS_PORT_ID, EFFECTIVE_DATE)

    job.execute()

    verify(exactly = 1) { etfBreakdownService.evictBreakdownCache() }
  }

  @Test
  fun `cannot evict the breakdown cache when every fund was already stored`() {
    stubFund(VGLA_PORT_ID, EFFECTIVE_DATE)
    stubFund(VXUS_PORT_ID, EFFECTIVE_DATE)
    every { etfHoldingService.hasHoldingsForDate(any(), EFFECTIVE_DATE) } returns true

    job.execute()

    verify(exactly = 0) { etfBreakdownService.evictBreakdownCache() }
  }

  @Test
  fun `should save the healthy fund when the other fund fails`() {
    every { vanguardHoldingsService.fetchHoldings(VGLA_PORT_ID) } throws IllegalStateException("mixed effective dates")
    stubFund(VXUS_PORT_ID, EFFECTIVE_DATE)

    runCatching { job.execute() }

    verify(exactly = 1) { etfHoldingService.saveHoldings(VXUS, EFFECTIVE_DATE, any()) }
  }

  @Test
  fun `should fail the run when a fund fails`() {
    every { vanguardHoldingsService.fetchHoldings(VGLA_PORT_ID) } throws IllegalStateException("mixed effective dates")
    stubFund(VXUS_PORT_ID, EFFECTIVE_DATE)

    expect { job.execute() }.toThrow<IllegalStateException>()
  }

  @Test
  fun `cannot save holdings for a fund that failed`() {
    every { vanguardHoldingsService.fetchHoldings(VGLA_PORT_ID) } throws IllegalStateException("mixed effective dates")
    stubFund(VXUS_PORT_ID, EFFECTIVE_DATE)

    runCatching { job.execute() }

    verify(exactly = 0) { etfHoldingService.saveHoldings(VGLA, any(), any()) }
  }

  @Test
  fun `should have correct job name`() {
    expect(job.getName()).toEqual("VanguardHoldingsRetrievalJob")
  }

  @Test
  fun `should schedule a startup import and a nightly cron import`() {
    val startup =
      VanguardHoldingsRetrievalJob::class.java
        .getMethod("runStartupImport")
        .getAnnotationsByType(Scheduled::class.java)
    val nightly =
      VanguardHoldingsRetrievalJob::class.java
        .getMethod("runNightlyImport")
        .getAnnotationsByType(Scheduled::class.java)
    expect(startup.single().initialDelay).toEqual(60000L)
    expect(startup.single().fixedDelay).toEqual(Long.MAX_VALUE)
    CronExpression.parse(nightly.single().cron)
    expect(nightly.single().cron).toEqual("0 30 2 * * *")
  }

  private fun stubFund(
    portId: String,
    effectiveDate: LocalDate,
  ) {
    every { vanguardHoldingsService.fetchHoldings(portId) } returns
      VanguardFundSnapshot(
        effectiveDate = effectiveDate,
        holdings = listOf(HoldingData(name = "Apple Inc", ticker = "AAPL", sector = null, weight = BigDecimal("100"), rank = 1)),
      )
  }

  companion object {
    private const val VGLA = "VGLA:GER:EUR"
    private const val VXUS = "VXUS:GER:EUR"
    private val VGLA_PORT_ID = VanguardHoldingsService.FUNDS.getValue(VGLA)
    private val VXUS_PORT_ID = VanguardHoldingsService.FUNDS.getValue(VXUS)
    private val EFFECTIVE_DATE = LocalDate.of(2026, 8, 31)
  }
}
