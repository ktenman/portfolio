package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.VanguardHoldingUpdates
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.model.CollectionRunResult
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.service.etf.EtfHoldingCountryService
import ee.tenman.portfolio.service.etf.EtfHoldingIndustryService
import ee.tenman.portfolio.service.etf.EtfHoldingService
import ee.tenman.portfolio.service.infrastructure.CacheInvalidationService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.testing.fixture.monitorForTests
import ee.tenman.portfolio.vanguard.VanguardFundSnapshot
import ee.tenman.portfolio.vanguard.VanguardHoldingsService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.support.CronExpression
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class VanguardHoldingsRetrievalJobTest {
  private val vanguardHoldingsService: VanguardHoldingsService = mockk()
  private val etfHoldingService: EtfHoldingService = mockk(relaxed = true)
  private val cacheInvalidationService: CacheInvalidationService = mockk(relaxed = true)
  private val etfHoldingIndustryService: EtfHoldingIndustryService = mockk(relaxed = true)
  private val etfHoldingsClassificationJob: EtfHoldingsClassificationJob = mockk(relaxed = true)
  private val jobExecutionService: JobExecutionService = mockk(relaxed = true)
  private val etfPositionRepository: EtfPositionRepository = mockk()
  private val lightyearPriceService: LightyearPriceService = mockk()
  private val clock = Clock.fixed(Instant.parse("2026-09-23T00:15:00Z"), ZoneOffset.UTC)
  private val collections = mutableListOf<CollectionRunResult>()

  private val job =
    VanguardHoldingsRetrievalJob(
      vanguardHoldingsService = vanguardHoldingsService,
      etfHoldingService = etfHoldingService,
      cacheInvalidationService = cacheInvalidationService,
      etfHoldingIndustryService = etfHoldingIndustryService,
      etfHoldingCountryService = mockk<EtfHoldingCountryService>(relaxed = true),
      etfHoldingsClassificationJob = etfHoldingsClassificationJob,
      jobExecutionService = jobExecutionService,
      etfPositionRepository = etfPositionRepository,
      lightyearPriceService = lightyearPriceService,
      clock = clock,
      collectionMonitor = monitorForTests(collections),
    )

  @BeforeEach
  fun setup() {
    every { etfHoldingService.resolveVanguardUpdates(any()) } returns VanguardHoldingUpdates(emptyList(), emptyList())
    every { etfHoldingService.hasHoldingsForDate(any(), any()) } returns false
    every { etfPositionRepository.deleteBySymbolAndSnapshotDateAfter(any(), any()) } returns 0
    every { etfPositionRepository.findLatestSnapshotDate(any()) } returns EFFECTIVE_DATE
    every { lightyearPriceService.fetchHoldingsAsDto(any()) } returns emptyList()
    listOf("E161", "E165", "9694", "9679", "9680", "9523", "9681").forEach { stubFund(it, EFFECTIVE_DATE) }
  }

  @Test
  fun `should reconcile metadata at startup even when all funds already have positions`() {
    every { etfPositionRepository.existsByEtfInstrumentSymbol(any()) } returns true

    job.runStartupImport()

    verifyOrder {
      jobExecutionService.executeJob(job)
      jobExecutionService.executeJob(etfHoldingsClassificationJob)
    }
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
  fun `should import all seven funds using their Vanguard port ids`() {
    job.execute()
    verifyOrder {
      vanguardHoldingsService.fetchHoldings("E161")
      etfHoldingService.saveHoldings("VGLA:GER:EUR", EFFECTIVE_DATE, any())
      vanguardHoldingsService.fetchHoldings("E165")
      etfHoldingService.saveHoldings("VXUS:GER:EUR", EFFECTIVE_DATE, any())
      vanguardHoldingsService.fetchHoldings("9694")
      etfHoldingService.saveHoldings("VUAA:GER:EUR", EFFECTIVE_DATE, any())
      vanguardHoldingsService.fetchHoldings("9679")
      etfHoldingService.saveHoldings("VWCE:GER:EUR", EFFECTIVE_DATE, any())
      vanguardHoldingsService.fetchHoldings("9680")
      etfHoldingService.saveHoldings("VNRA:GER:EUR", EFFECTIVE_DATE, any())
      vanguardHoldingsService.fetchHoldings("9523")
      etfHoldingService.saveHoldings("VNRT:AEX:EUR", EFFECTIVE_DATE, any())
      vanguardHoldingsService.fetchHoldings("9681")
      etfHoldingService.saveHoldings("VWCG:GER:EUR", EFFECTIVE_DATE, any())
    }
  }

  @Test
  fun `should import on startup when VWCE has no positions`() {
    every { etfPositionRepository.existsByEtfInstrumentSymbol(any()) } returns true
    every { etfPositionRepository.existsByEtfInstrumentSymbol("VWCE:GER:EUR") } returns false
    job.runStartupImport()
    verify(exactly = 1) { jobExecutionService.executeJob(job) }
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

    verify(exactly = 1) { cacheInvalidationService.evictEtfBreakdownCache() }
  }

  @Test
  fun `cannot evict the breakdown cache when every fund was already stored`() {
    stubFund(VGLA_PORT_ID, EFFECTIVE_DATE)
    stubFund(VXUS_PORT_ID, EFFECTIVE_DATE)
    every { etfHoldingService.hasHoldingsForDate(any(), EFFECTIVE_DATE) } returns true

    job.execute()

    verify(exactly = 0) { cacheInvalidationService.evictEtfBreakdownCache() }
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
    expect(collections.single().failed).toContain(VGLA)
    expect(collections.single().persisted).toContain(VXUS)
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
  fun `should delete newer snapshots after saving each Vanguard snapshot`() {
    stubFund(VXUS_PORT_ID, LocalDate.of(2026, 7, 31))
    job.execute()
    verifyOrder {
      etfHoldingService.saveHoldings(VGLA, EFFECTIVE_DATE, any())
      etfPositionRepository.deleteBySymbolAndSnapshotDateAfter(VGLA, EFFECTIVE_DATE)
      etfHoldingService.saveHoldings(VXUS, LocalDate.of(2026, 7, 31), any())
      etfPositionRepository.deleteBySymbolAndSnapshotDateAfter(VXUS, LocalDate.of(2026, 7, 31))
    }
  }

  @Test
  fun `should delete newer snapshots even when the effective date is already stored`() {
    every { etfHoldingService.hasHoldingsForDate(any(), EFFECTIVE_DATE) } returns true
    job.execute()
    verify(exactly = 1) { etfPositionRepository.deleteBySymbolAndSnapshotDateAfter(VGLA, EFFECTIVE_DATE) }
  }

  @Test
  fun `cannot delete snapshots when Vanguard fails`() {
    every { vanguardHoldingsService.fetchHoldings(VGLA_PORT_ID) } throws IllegalStateException("Vanguard unavailable")
    runCatching { job.execute() }
    verify(exactly = 0) { etfPositionRepository.deleteBySymbolAndSnapshotDateAfter(VGLA, any()) }
  }

  @Test
  fun `cannot delete newer snapshots when saving Vanguard holdings fails`() {
    every { etfHoldingService.saveHoldings(VGLA, EFFECTIVE_DATE, any()) } throws IllegalStateException("Database unavailable")
    runCatching { job.execute() }
    verify(exactly = 0) { etfPositionRepository.deleteBySymbolAndSnapshotDateAfter(VGLA, any()) }
  }

  @Test
  fun `should evict the breakdown cache when only newer snapshots were deleted`() {
    every { etfHoldingService.hasHoldingsForDate(any(), EFFECTIVE_DATE) } returns true
    every { etfPositionRepository.deleteBySymbolAndSnapshotDateAfter(VGLA, EFFECTIVE_DATE) } returns 3
    job.execute()
    verify(exactly = 1) { cacheInvalidationService.evictEtfBreakdownCache() }
  }

  @Test
  fun `should evict the breakdown cache when cleanup fails after a committed save`() {
    val failure = IllegalStateException("Snapshot cleanup failed")
    every { etfHoldingService.hasHoldingsForDate(any(), EFFECTIVE_DATE) } returns true
    every { etfHoldingService.hasHoldingsForDate(VGLA, EFFECTIVE_DATE) } returnsMany listOf(false, true)
    every { etfPositionRepository.deleteBySymbolAndSnapshotDateAfter(VGLA, EFFECTIVE_DATE) } throws failure andThen 0
    val result = runCatching { job.execute() }
    job.execute()
    expect(result.exceptionOrNull()).toEqual(failure)
    verifyOrder {
      etfHoldingService.saveHoldings(VGLA, EFFECTIVE_DATE, any())
      etfPositionRepository.deleteBySymbolAndSnapshotDateAfter(VGLA, EFFECTIVE_DATE)
      cacheInvalidationService.evictEtfBreakdownCache()
      etfPositionRepository.deleteBySymbolAndSnapshotDateAfter(VGLA, EFFECTIVE_DATE)
    }
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = ["2026-07-22"])
  fun `should save Lightyear holdings for a missing or stale snapshot after Vanguard fails`(latest: String?) {
    val holdings = listOf(HoldingData(name = "Roche Holding", ticker = "RO", sector = null, weight = BigDecimal("100"), rank = 1))
    every { vanguardHoldingsService.fetchHoldings(VXUS_PORT_ID) } throws IllegalStateException("Vanguard unavailable")
    every { etfPositionRepository.findLatestSnapshotDate(VXUS) } returns latest?.let(LocalDate::parse)
    every { lightyearPriceService.fetchHoldingsAsDto(VXUS) } returns holdings
    runCatching { job.execute() }
    verify(exactly = 1) { etfHoldingService.saveHoldings(VXUS, TODAY, holdings) }
  }

  @ParameterizedTest
  @ValueSource(strings = ["2026-07-23", "2026-07-24", "2026-09-24"])
  fun `cannot fall back when the latest snapshot is at most two months old`(latest: String) {
    every { vanguardHoldingsService.fetchHoldings(VGLA_PORT_ID) } throws IllegalStateException("Vanguard unavailable")
    every { etfPositionRepository.findLatestSnapshotDate(VGLA) } returns LocalDate.parse(latest)
    runCatching { job.execute() }
    verify(exactly = 0) { lightyearPriceService.fetchHoldingsAsDto(any()) }
  }

  @Test
  fun `cannot save an empty Lightyear fallback`() {
    every { vanguardHoldingsService.fetchHoldings(VGLA_PORT_ID) } throws IllegalStateException("Vanguard unavailable")
    every { etfPositionRepository.findLatestSnapshotDate(VGLA) } returns null
    runCatching { job.execute() }
    verify(exactly = 1) { lightyearPriceService.fetchHoldingsAsDto(VGLA) }
    verify(exactly = 0) { etfHoldingService.saveHoldings(VGLA, any(), any()) }
  }

  @Test
  fun `should rethrow the Vanguard failure after a fallback save`() {
    val failure = IllegalStateException("Vanguard unavailable")
    stubFallback(failure)
    expect(runCatching { job.execute() }.exceptionOrNull()).toEqual(failure)
  }

  @ParameterizedTest
  @ValueSource(strings = ["lookup", "fetch", "save"])
  fun `should preserve the Vanguard failure when the fallback fails`(stage: String) {
    val failure = IllegalStateException("Vanguard unavailable")
    val fallback = IllegalArgumentException("Lightyear fallback unavailable")
    stubFallback(failure)
    when (stage) {
      "lookup" -> every { etfPositionRepository.findLatestSnapshotDate(VGLA) } throws fallback
      "fetch" -> every { lightyearPriceService.fetchHoldingsAsDto(VGLA) } throws fallback
      "save" -> every { etfHoldingService.saveHoldings(VGLA, TODAY, any()) } throws fallback
    }
    val result = runCatching { job.execute() }
    verify(exactly = 1) { etfHoldingService.saveHoldings(VXUS, EFFECTIVE_DATE, any()) }
    expect(result.exceptionOrNull()).toEqual(failure)
  }

  @Test
  fun `should evict the breakdown cache after only a fallback was saved`() {
    every { etfHoldingService.hasHoldingsForDate(any(), EFFECTIVE_DATE) } returns true
    stubFallback(IllegalStateException("Vanguard unavailable"))
    runCatching { job.execute() }
    verifyOrder {
      etfHoldingService.saveHoldings(VGLA, TODAY, any())
      cacheInvalidationService.evictEtfBreakdownCache()
    }
  }

  @Test
  fun `cannot evict the breakdown cache when all Vanguard fetches fail without a fallback save`() {
    every { vanguardHoldingsService.fetchHoldings(any()) } throws IllegalStateException("Vanguard unavailable")
    every { etfPositionRepository.findLatestSnapshotDate(any()) } returns null
    runCatching { job.execute() }
    verify(exactly = 0) { cacheInvalidationService.evictEtfBreakdownCache() }
  }

  @Test
  fun `should skip the fallback on retry when todays snapshot is already saved`() {
    stubFallback(IllegalStateException("Vanguard unavailable"))
    every { etfPositionRepository.findLatestSnapshotDate(VGLA) } returnsMany listOf(null, TODAY)
    repeat(2) { runCatching { job.execute() } }
    verify(exactly = 1) { lightyearPriceService.fetchHoldingsAsDto(VGLA) }
    verify(exactly = 1) { etfHoldingService.saveHoldings(VGLA, TODAY, any()) }
  }

  private fun stubFallback(failure: Throwable) {
    every { vanguardHoldingsService.fetchHoldings(VGLA_PORT_ID) } throws failure
    every { etfPositionRepository.findLatestSnapshotDate(VGLA) } returns null
    every { lightyearPriceService.fetchHoldingsAsDto(VGLA) } returns
      listOf(HoldingData(name = "Roche Holding", ticker = "RO", sector = null, weight = BigDecimal("100"), rank = 1))
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
    private val TODAY = LocalDate.of(2026, 9, 23)
  }
}
