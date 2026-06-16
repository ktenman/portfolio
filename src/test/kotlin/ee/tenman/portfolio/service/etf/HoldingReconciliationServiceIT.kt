package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import com.ninjasquad.springmockk.MockkBean
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import io.mockk.every
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@IntegrationTest
class HoldingReconciliationServiceIT {
  @Resource
  private lateinit var holdingReconciliationService: HoldingReconciliationService

  @MockkBean(relaxed = true)
  private lateinit var holdingIdentityService: HoldingIdentityService

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var etfHoldingRepository: EtfHoldingRepository

  @Resource
  private lateinit var etfPositionRepository: EtfPositionRepository

  private lateinit var etf: Instrument

  @BeforeEach
  fun setUp() {
    etfPositionRepository.deleteAll()
    etfHoldingRepository.deleteAll()
    instrumentRepository.deleteAll()
    etf =
      instrumentRepository.save(
        Instrument(symbol = "IITU", name = "iShares S&P 500 IT", category = "ETF", baseCurrency = "EUR"),
      )
  }

  @Test
  fun `should merge confirmed duplicate holdings sharing a block key`() {
    etfHoldingRepository.save(EtfHolding(name = "NVIDIA"))
    etfHoldingRepository.save(EtfHolding(name = "NVIDIA CORP", ticker = "NVDA"))
    every { holdingIdentityService.isSameCompany("NVIDIA", "NVIDIA CORP", any()) } returns true

    holdingReconciliationService.reconcile(dryRun = false)

    expect(etfHoldingRepository.findAll().map { it.name }).toContainExactly("NVIDIA")
  }

  @Test
  fun `cannot merge distinct companies that share a block key`() {
    etfHoldingRepository.save(EtfHolding(name = "Merck & Co"))
    etfHoldingRepository.save(EtfHolding(name = "Merck KGaA"))
    every { holdingIdentityService.isSameCompany("Merck & Co", "Merck KGaA", any()) } returns false

    holdingReconciliationService.reconcile(dryRun = false)

    expect(etfHoldingRepository.findAll()).toHaveSize(2)
  }

  @Test
  fun `dont modify holdings when reconciling in dry run mode`() {
    etfHoldingRepository.save(EtfHolding(name = "NVIDIA"))
    etfHoldingRepository.save(EtfHolding(name = "NVIDIA CORP", ticker = "NVDA"))
    every { holdingIdentityService.isSameCompany("NVIDIA", "NVIDIA CORP", any()) } returns true

    holdingReconciliationService.reconcile(dryRun = true)

    expect(etfHoldingRepository.findAll()).toHaveSize(2)
  }

  @Test
  fun `should keep distinct cluster standalone while merging confirmed duplicates in one block`() {
    etfHoldingRepository.save(EtfHolding(name = "Alpha"))
    etfHoldingRepository.save(EtfHolding(name = "Alpha Inc"))
    etfHoldingRepository.save(EtfHolding(name = "Alpha Foods"))
    every { holdingIdentityService.isSameCompany("Alpha", "Alpha Inc", any()) } returns true
    every { holdingIdentityService.isSameCompany("Alpha", "Alpha Foods", any()) } returns false

    holdingReconciliationService.reconcile(dryRun = false)

    expect(etfHoldingRepository.findAll().map { it.name }.sorted()).toContainExactly("Alpha", "Alpha Foods")
  }

  @Test
  fun `should collapse a transitive duplicate cluster when a holding matches a non representative`() {
    etfHoldingRepository.save(EtfHolding(name = "Beta"))
    etfHoldingRepository.save(EtfHolding(name = "Beta Corp"))
    etfHoldingRepository.save(EtfHolding(name = "Beta Industries"))
    every { holdingIdentityService.isSameCompany("Beta", "Beta Corp", any()) } returns true
    every { holdingIdentityService.isSameCompany("Beta", "Beta Industries", any()) } returns false
    every { holdingIdentityService.isSameCompany("Beta Corp", "Beta Industries", any()) } returns true

    holdingReconciliationService.reconcile(dryRun = false)

    expect(etfHoldingRepository.findAll().map { it.name }).toContainExactly("Beta")
  }

  @Test
  fun `should report would be merges in dry run mode without mutating`() {
    etfHoldingRepository.save(EtfHolding(name = "NVIDIA"))
    etfHoldingRepository.save(EtfHolding(name = "NVIDIA CORP", ticker = "NVDA"))
    every { holdingIdentityService.isSameCompany("NVIDIA", "NVIDIA CORP", any()) } returns true

    val result = holdingReconciliationService.reconcile(dryRun = true)

    expect(result.mergedGroups to result.mergedDuplicates).toEqual(1 to 1)
  }

  @Test
  fun `should report no merges when no block key has duplicates`() {
    etfHoldingRepository.save(EtfHolding(name = "Apple Inc", ticker = "AAPL"))

    val result = holdingReconciliationService.reconcile(dryRun = false)

    expect(result.mergedGroups).toEqual(0)
  }

  @Test
  fun `should be idempotent and report zero duplicates on a second reconcile`() {
    etfHoldingRepository.save(EtfHolding(name = "NVIDIA"))
    etfHoldingRepository.save(EtfHolding(name = "NVIDIA CORP", ticker = "NVDA"))
    every { holdingIdentityService.isSameCompany("NVIDIA", "NVIDIA CORP", any()) } returns true
    holdingReconciliationService.reconcile(dryRun = false)

    val second = holdingReconciliationService.reconcile(dryRun = false)

    expect(second.mergedGroups).toEqual(0)
  }

  @Test
  fun `should repoint positions onto canonical when merging duplicates`() {
    val canonical = etfHoldingRepository.save(EtfHolding(name = "NVIDIA"))
    val duplicate = etfHoldingRepository.save(EtfHolding(name = "NVIDIA CORP", ticker = "NVDA"))
    savePosition(canonical, LocalDate.of(2024, 3, 1))
    savePosition(duplicate, LocalDate.of(2024, 3, 2))
    every { holdingIdentityService.isSameCompany("NVIDIA", "NVIDIA CORP", any()) } returns true

    holdingReconciliationService.reconcile(dryRun = false)

    expect(etfPositionRepository.findAll().map { it.holding.id }.distinct()).toEqual(listOf(canonical.id))
  }

  private fun savePosition(
    holding: EtfHolding,
    date: LocalDate,
  ) {
    etfPositionRepository.save(
      EtfPosition(etfInstrument = etf, holding = holding, snapshotDate = date, weightPercentage = BigDecimal("5.0"), positionRank = 1),
    )
  }
}
