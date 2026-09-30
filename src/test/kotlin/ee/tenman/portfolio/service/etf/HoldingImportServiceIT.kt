package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import com.ninjasquad.springmockk.MockkBean
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.GicsIndustry
import ee.tenman.portfolio.domain.SectorSource
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.repository.PendingHoldingImportRepository
import ee.tenman.portfolio.service.infrastructure.CacheInvalidationService
import io.mockk.every
import io.mockk.verify
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@IntegrationTest
class HoldingImportServiceIT {
  @Resource
  private lateinit var holdingImportService: HoldingImportService

  @Resource
  private lateinit var pendingHoldingImportRepository: PendingHoldingImportRepository

  @MockkBean(relaxed = true)
  private lateinit var etfHoldingService: EtfHoldingService

  @MockkBean(relaxed = true)
  private lateinit var cacheInvalidationService: CacheInvalidationService

  @Test
  fun `should publish the queued holdings with every field intact`() {
    holdingImportService.queue(SYMBOL, DATE, HOLDINGS)
    holdingImportService.importPending()
    verify { etfHoldingService.saveHoldings(SYMBOL, DATE, HOLDINGS) }
  }

  @Test
  fun `should evict the etf breakdown cache after publishing`() {
    holdingImportService.queue(SYMBOL, DATE, HOLDINGS)
    holdingImportService.importPending()
    verify { cacheInvalidationService.evictEtfBreakdownCache() }
  }

  @Test
  fun `should publish only the newest snapshot of a fund`() {
    holdingImportService.queue(SYMBOL, DATE.minusDays(1), REFETCHED)
    holdingImportService.queue(SYMBOL, DATE, HOLDINGS)
    holdingImportService.importPending()
    verify(exactly = 0) { etfHoldingService.saveHoldings(SYMBOL, DATE.minusDays(1), any()) }
  }

  @Test
  fun `should drop older snapshots of a published fund`() {
    holdingImportService.queue(SYMBOL, DATE.minusDays(1), REFETCHED)
    holdingImportService.queue(SYMBOL, DATE, HOLDINGS)
    holdingImportService.importPending()
    expect(pendingHoldingImportRepository.count()).toEqual(0L)
  }

  @Test
  fun `should keep the snapshot queued when publishing fails`() {
    every { etfHoldingService.saveHoldings(SYMBOL, DATE, any()) } throws IllegalArgumentException("Holding name cannot be blank")
    holdingImportService.queue(SYMBOL, DATE, HOLDINGS)
    runCatching { holdingImportService.importPending() }
    expect(pendingHoldingImportRepository.count()).toEqual(1L)
  }

  @Test
  fun `should name the fund whose import failed`() {
    every { etfHoldingService.saveHoldings(SYMBOL, DATE, any()) } throws IllegalArgumentException("Holding name cannot be blank")
    holdingImportService.queue(SYMBOL, DATE, HOLDINGS)
    expect { holdingImportService.importPending() }.toThrow<IllegalStateException>().messageToContain(SYMBOL)
  }

  @Test
  fun `should publish other funds when one fund fails`() {
    every { etfHoldingService.saveHoldings(SYMBOL, DATE, any()) } throws IllegalArgumentException("Holding name cannot be blank")
    holdingImportService.queue(SYMBOL, DATE, HOLDINGS)
    holdingImportService.queue(OTHER_SYMBOL, DATE, REFETCHED)
    runCatching { holdingImportService.importPending() }
    verify { etfHoldingService.saveHoldings(OTHER_SYMBOL, DATE, REFETCHED) }
  }

  @Test
  fun `should import a snapshot queued during a publish on the next run`() {
    every { etfHoldingService.saveHoldings(SYMBOL, DATE, HOLDINGS) } answers { holdingImportService.queue(SYMBOL, DATE, REFETCHED) }
    holdingImportService.queue(SYMBOL, DATE, HOLDINGS)
    holdingImportService.importPending()
    holdingImportService.importPending()
    verify { etfHoldingService.saveHoldings(SYMBOL, DATE, REFETCHED) }
  }

  @Test
  fun `should reject an empty snapshot`() {
    expect { holdingImportService.queue(SYMBOL, DATE, emptyList()) }.toThrow<IllegalArgumentException>()
  }

  @Test
  fun `should reject a snapshot with an unnamed holding`() {
    expect { holdingImportService.queue(SYMBOL, DATE, UNNAMED) }.toThrow<IllegalArgumentException>()
  }

  @Test
  fun `should report a queued snapshot as pending`() {
    holdingImportService.queue(SYMBOL, DATE, HOLDINGS)
    expect(holdingImportService.hasPending()).toEqual(true)
  }

  @Test
  fun `should report nothing pending when the queue is empty`() {
    expect(holdingImportService.hasPending()).toEqual(false)
  }

  companion object {
    private const val SYMBOL = "EE3600001707"
    private const val OTHER_SYMBOL = "IE00BK5BQT80"
    private val DATE = LocalDate.of(2026, 9, 29)
    private val HOLDINGS =
      listOf(
        HoldingData(
          name = "Dassault Systèmes SE",
          ticker = "DSY",
          sector = "Information Technology",
          weight = BigDecimal("1.230"),
          rank = 1,
          logoUrl = "https://example.com/dassault-systèmes.png",
          countryCode = "FR",
          countryName = "Prantsusmaa",
          sectorSource = SectorSource.LLM,
          industry = GicsIndustry.SOFTWARE,
        ),
        HoldingData(name = "Hermès International", ticker = null, sector = null, weight = BigDecimal("0.0262"), rank = 2),
      )
    private val REFETCHED = listOf(HoldingData(name = "Nestlé SA", ticker = "NESN", sector = null, weight = BigDecimal("0.50"), rank = 1))
    private val UNNAMED = listOf(HoldingData(name = " ", ticker = null, sector = null, weight = BigDecimal("0.50"), rank = 1))
  }
}
