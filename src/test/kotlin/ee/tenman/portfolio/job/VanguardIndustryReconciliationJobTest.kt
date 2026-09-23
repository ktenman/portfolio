package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.GicsIndustry
import ee.tenman.portfolio.domain.VanguardIndustryUpdate
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.service.etf.EtfHoldingIndustryService
import ee.tenman.portfolio.service.etf.EtfHoldingService
import ee.tenman.portfolio.service.infrastructure.CacheInvalidationService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.vanguard.VanguardFundSnapshot
import ee.tenman.portfolio.vanguard.VanguardHoldingsService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class VanguardIndustryReconciliationJobTest {
  private val vanguard = mockk<VanguardHoldingsService>()
  private val holdings = mockk<EtfHoldingService>(relaxed = true)
  private val industries = mockk<EtfHoldingIndustryService>(relaxed = true)
  private val positions = mockk<EtfPositionRepository>(relaxed = true)
  private val caches = mockk<CacheInvalidationService>(relaxed = true)
  private val date = LocalDate.of(2026, 8, 31)
  private val update = VanguardIndustryUpdate(UUID.randomUUID(), GicsIndustry.PHARMACEUTICALS, date)
  private val snapshot =
    VanguardFundSnapshot(
      date,
      listOf(HoldingData("Haleon PLC", "HLN", null, BigDecimal("0.37"), 19, industry = GicsIndustry.PHARMACEUTICALS)),
    )
  private val job =
    VanguardHoldingsRetrievalJob(
      vanguard,
      holdings,
      caches,
      industries,
      mockk<EtfHoldingsClassificationJob>(),
      mockk<JobExecutionService>(),
      positions,
      mockk<LightyearPriceService>(),
      Clock.fixed(Instant.parse("2026-09-23T12:00:00Z"), ZoneOffset.UTC),
    )

  @BeforeEach
  fun setup() {
    every { vanguard.fetchHoldings(any()) } returns snapshot
    every { holdings.hasHoldingsForDate(any(), any()) } returns true
    every { holdings.resolveIndustryUpdates(snapshot.holdings, date) } returns listOf(update)
    every { positions.findLatestSnapshotDate(any()) } returns date
  }

  @Test
  fun `should reconcile industries from all funds even when snapshots already exist`() {
    job.execute()

    verify(exactly = 1) { industries.updateVanguardIndustries(List(7) { update }) }
    verify(exactly = 0) { holdings.saveHoldings(any(), any(), any()) }
  }

  @Test
  fun `should defer source industries to validated reconciliation when saving positions`() {
    every { holdings.hasHoldingsForDate("VGLA:GER:EUR", date) } returns false

    job.execute()

    verify(exactly = 1) {
      holdings.saveHoldings("VGLA:GER:EUR", date, snapshot.holdings.map { it.copy(industry = null) })
    }
    verify(exactly = 7) { holdings.resolveIndustryUpdates(snapshot.holdings, date) }
  }

  @Test
  fun `should invalidate both caches after only industries change`() {
    every { industries.updateVanguardIndustries(any()) } returns 1

    job.execute()

    verify(exactly = 1) { caches.evictEtfBreakdownCache() }
    verify(exactly = 1) { caches.evictDiversificationEtfsCache() }
  }

  @Test
  fun `cannot invalidate caches when snapshots and industries are unchanged`() {
    job.execute()

    verify(exactly = 0) { caches.evictEtfBreakdownCache() }
    verify(exactly = 0) { caches.evictDiversificationEtfsCache() }
  }

  @Test
  fun `should reconcile successful fund industries while preserving a fetch failure`() {
    val failure = IllegalStateException("Vanguard E161 unavailable")
    every { vanguard.fetchHoldings("E161") } throws failure
    every { industries.updateVanguardIndustries(any()) } returns 1

    expect { job.execute() }.toThrow<IllegalStateException>().toEqual(failure)

    verify(exactly = 1) { industries.updateVanguardIndustries(List(6) { update }) }
    verify(exactly = 1) { caches.evictEtfBreakdownCache() }
    verify(exactly = 1) { caches.evictDiversificationEtfsCache() }
  }

  @Test
  fun `should preserve the original fetch failure when industry reconciliation also fails`() {
    val failure = IllegalStateException("Vanguard E161 unavailable")
    every { vanguard.fetchHoldings("E161") } throws failure
    every { industries.updateVanguardIndustries(any()) } throws IllegalArgumentException("Industry update failed")

    expect { job.execute() }.toThrow<IllegalStateException>().toEqual(failure)
  }

  @Test
  fun `should invalidate committed snapshot changes when industry reconciliation fails`() {
    val failure = IllegalStateException("Industry update failed")
    every { holdings.hasHoldingsForDate("VGLA:GER:EUR", date) } returns false
    every { industries.updateVanguardIndustries(any()) } throws failure

    expect { job.execute() }.toThrow<IllegalStateException>().toEqual(failure)

    verify(exactly = 1) { caches.evictEtfBreakdownCache() }
    verify(exactly = 1) { caches.evictDiversificationEtfsCache() }
  }

  @Test
  fun `should retain industry observations when newer snapshot cleanup fails`() {
    val failure = IllegalStateException("Snapshot cleanup failed")
    every { positions.deleteBySymbolAndSnapshotDateAfter("VGLA:GER:EUR", date) } throws failure

    expect { job.execute() }.toThrow<IllegalStateException>().toEqual(failure)

    verify(exactly = 1) { industries.updateVanguardIndustries(List(7) { update }) }
  }
}
