package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.IndustrySector
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@IntegrationTest
class HoldingMergeServiceIT {
  @Resource
  private lateinit var holdingMergeService: HoldingMergeService

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var etfHoldingRepository: EtfHoldingRepository

  @Resource
  private lateinit var etfPositionRepository: EtfPositionRepository

  private lateinit var etf: Instrument

  private val firstDate = LocalDate.of(2024, 3, 1)

  private val secondDate = LocalDate.of(2024, 3, 2)

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
  fun `should repoint duplicate positions onto canonical holding`() {
    val canonical = etfHoldingRepository.save(EtfHolding(name = "NVIDIA"))
    val duplicate = etfHoldingRepository.save(EtfHolding(name = "NVIDIA CORP", ticker = "NVDA"))
    savePosition(canonical, firstDate, BigDecimal("5.0"))
    savePosition(duplicate, secondDate, BigDecimal("4.0"))

    holdingMergeService.merge(canonical.id, listOf(duplicate.id))

    val holdingIds = etfPositionRepository.findAll().map { it.holding.id }.distinct()
    expect(holdingIds).toEqual(listOf(canonical.id))
  }

  @Test
  fun `should delete colliding duplicate position keeping canonical weight`() {
    val canonical = etfHoldingRepository.save(EtfHolding(name = "Micron"))
    val duplicate = etfHoldingRepository.save(EtfHolding(name = "Micron Technology Inc", ticker = "MU"))
    savePosition(canonical, firstDate, BigDecimal("5.0"))
    savePosition(duplicate, firstDate, BigDecimal("9.9"))

    holdingMergeService.merge(canonical.id, listOf(duplicate.id))

    expect(etfPositionRepository.findAll().single().weightPercentage).toEqualNumerically(BigDecimal("5.0"))
  }

  @Test
  fun `should backfill missing ticker and sector onto canonical and delete duplicate`() {
    val canonical = etfHoldingRepository.save(EtfHolding(name = "NVIDIA"))
    val duplicate =
      etfHoldingRepository.save(EtfHolding(name = "NVIDIA CORP", ticker = "NVDA", sector = IndustrySector.INDUSTRIALS))

    holdingMergeService.merge(canonical.id, listOf(duplicate.id))

    val surviving = etfHoldingRepository.findAll().single()
    expect(surviving.ticker to surviving.sector).toEqual("NVDA" to IndustrySector.INDUSTRIALS)
  }

  @Test
  fun `should backfill missing country onto canonical and delete duplicate`() {
    val canonical = etfHoldingRepository.save(EtfHolding(name = "NVIDIA"))
    val duplicate =
      etfHoldingRepository.save(
        EtfHolding(name = "NVIDIA CORP", ticker = "NVDA", countryCode = "US", countryName = "United States"),
      )

    holdingMergeService.merge(canonical.id, listOf(duplicate.id))

    val surviving = etfHoldingRepository.findAll().single()
    expect(surviving.countryCode to surviving.countryName).toEqual("US" to "United States")
  }

  @Test
  fun `should keep lower id duplicate position when two duplicates collide on same date`() {
    val canonical = etfHoldingRepository.save(EtfHolding(name = "Beta"))
    val firstDuplicate = etfHoldingRepository.save(EtfHolding(name = "Beta Corp", ticker = "BTA"))
    val secondDuplicate = etfHoldingRepository.save(EtfHolding(name = "Beta Inc", ticker = "BTB"))
    savePosition(firstDuplicate, firstDate, BigDecimal("7.0"))
    savePosition(secondDuplicate, firstDate, BigDecimal("8.0"))

    holdingMergeService.merge(canonical.id, listOf(firstDuplicate.id, secondDuplicate.id))

    val surviving = etfPositionRepository.findAll().single()
    expect(surviving.holding.id to surviving.weightPercentage.compareTo(BigDecimal("7.0"))).toEqual(canonical.id to 0)
  }

  @Test
  fun `cannot merge when duplicate list is empty`() {
    val canonical = etfHoldingRepository.save(EtfHolding(name = "Apple Inc", ticker = "AAPL"))

    holdingMergeService.merge(canonical.id, emptyList())

    expect(etfHoldingRepository.findAll()).toHaveSize(1)
  }

  private fun savePosition(
    holding: EtfHolding,
    date: LocalDate,
    weight: BigDecimal,
  ) {
    etfPositionRepository.save(
      EtfPosition(etfInstrument = etf, holding = holding, snapshotDate = date, weightPercentage = weight, positionRank = 1),
    )
  }
}
