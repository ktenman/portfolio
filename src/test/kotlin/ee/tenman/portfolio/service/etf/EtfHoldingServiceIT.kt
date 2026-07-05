package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import com.ninjasquad.springmockk.MockkBean
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.IndustrySector
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.dto.HoldingData
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
class EtfHoldingServiceIT {
  @Resource
  private lateinit var etfHoldingService: EtfHoldingService

  @MockkBean(relaxed = true)
  private lateinit var holdingIdentityService: HoldingIdentityService

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var etfHoldingRepository: EtfHoldingRepository

  @Resource
  private lateinit var etfPositionRepository: EtfPositionRepository

  private lateinit var testEtf: Instrument

  private val testDate = LocalDate.of(2024, 1, 15)

  @BeforeEach
  fun setup() {
    etfPositionRepository.deleteAll()
    etfHoldingRepository.deleteAll()
    instrumentRepository.deleteAll()
    testEtf =
      instrumentRepository.save(
        Instrument(
          symbol = "IITU",
          name = "iShares Global Clean Energy ETF",
          category = "ETF",
          baseCurrency = "EUR",
        ),
      )
  }

  @Test
  fun `should save holdings for ETF`() {
    val holdings =
      listOf(
        HoldingData(
          name = "Apple Inc",
          ticker = "AAPL",
          sector = "Technology",
          weight = BigDecimal("10.0"),
          rank = 1,
          logoUrl = null,
        ),
      )

    etfHoldingService.saveHoldings("IITU", testDate, holdings)

    val savedHoldings = etfHoldingRepository.findAll()
    expect(savedHoldings.size).toEqual(1)
    expect(savedHoldings.first().name).toEqual("Apple Inc")
    expect(savedHoldings.first().ticker).toEqual("AAPL")
  }

  @Test
  fun `should update sector from source when existing holding has no sector`() {
    val holdingsWithoutSector =
      listOf(
        HoldingData(
          name = "Google Inc",
          ticker = "GOOGL",
          sector = null,
          weight = BigDecimal("10.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingService.saveHoldings("IITU", testDate, holdingsWithoutSector)
    val savedWithoutSector = etfHoldingRepository.findAll().first()
    expect(savedWithoutSector.sector).toEqual(null)

    val holdingsWithSector =
      listOf(
        HoldingData(
          name = "Google Inc",
          ticker = "GOOGL",
          sector = "Digital Hardware",
          weight = BigDecimal("10.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingService.saveHoldings("IITU", testDate.plusDays(1), holdingsWithSector)

    val updatedHolding = etfHoldingRepository.findAll().first()
    expect(updatedHolding.sector).toEqual(IndustrySector.DIGITAL_HARDWARE)
    expect(updatedHolding.classifiedByModel).toEqual(null)
  }

  @Test
  fun `should not overwrite existing sector from source`() {
    val holdingsWithSector =
      listOf(
        HoldingData(
          name = "Facebook Inc",
          ticker = "FB",
          sector = "Digital Hardware",
          weight = BigDecimal("10.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingService.saveHoldings("IITU", testDate, holdingsWithSector)
    val savedWithSector = etfHoldingRepository.findAll().first()
    expect(savedWithSector.sector).toEqual(IndustrySector.DIGITAL_HARDWARE)

    val holdingsWithDifferentSector =
      listOf(
        HoldingData(
          name = "Facebook Inc",
          ticker = "FB",
          sector = "Communication",
          weight = BigDecimal("10.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingService.saveHoldings("IITU", testDate.plusDays(1), holdingsWithDifferentSector)

    val unchangedHolding = etfHoldingRepository.findAll().first()
    expect(unchangedHolding.sector).toEqual(IndustrySector.DIGITAL_HARDWARE)
  }

  @Test
  fun `should create separate holdings when same ticker has different company names`() {
    val usCompanyHoldings =
      listOf(
        HoldingData(
          name = "Merck & Co.",
          ticker = "MRK",
          sector = "Healthcare",
          weight = BigDecimal("10.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingService.saveHoldings("IITU", testDate, usCompanyHoldings)
    expect(etfHoldingRepository.findAll().size).toEqual(1)

    val germanCompanyHoldings =
      listOf(
        HoldingData(
          name = "Merck KGaA",
          ticker = "MRK",
          sector = "Healthcare",
          weight = BigDecimal("8.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingService.saveHoldings("IITU", testDate.plusDays(1), germanCompanyHoldings)

    val allHoldings = etfHoldingRepository.findAll()
    expect(allHoldings.size).toEqual(2)
    expect(allHoldings.map { it.name }.toSet()).toEqual(setOf("Merck & Co.", "Merck KGaA"))
    expect(allHoldings.all { it.ticker == "MRK" }).toEqual(true)
  }

  @Test
  fun `should reuse holding when identity service confirms same company under shared ticker`() {
    val abbreviatedName =
      listOf(
        HoldingData(
          name = "Amazon",
          ticker = "AMZN",
          sector = "Consumer Cyclical",
          weight = BigDecimal("10.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingService.saveHoldings("IITU", testDate, abbreviatedName)
    val originalId = etfHoldingRepository.findAll().first().id
    every { holdingIdentityService.isSameCompany("Amazon", "Amazon.com Inc", "AMZN") } returns true

    val legalName =
      listOf(
        HoldingData(
          name = "Amazon.com Inc",
          ticker = "AMZN",
          sector = "Consumer Cyclical",
          weight = BigDecimal("9.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingService.saveHoldings("IITU", testDate.plusDays(1), legalName)

    val allHoldings = etfHoldingRepository.findAll()
    expect(allHoldings.size).toEqual(1)
    expect(allHoldings.first().id).toEqual(originalId)
  }

  @Test
  fun `should create separate holding when identity verdict is unavailable`() {
    val abbreviatedName =
      listOf(
        HoldingData(name = "Amazon", ticker = "AMZN", sector = null, weight = BigDecimal("10.0"), rank = 1, logoUrl = null),
      )
    etfHoldingService.saveHoldings("IITU", testDate, abbreviatedName)
    every { holdingIdentityService.isSameCompany("Amazon", "Amazon.com Inc", "AMZN") } returns null

    val legalName =
      listOf(
        HoldingData(name = "Amazon.com Inc", ticker = "AMZN", sector = null, weight = BigDecimal("9.0"), rank = 1, logoUrl = null),
      )
    etfHoldingService.saveHoldings("IITU", testDate.plusDays(1), legalName)

    expect(etfHoldingRepository.findAll()).toHaveSize(2)
  }

  @Test
  fun `should reuse holding via block key without shared ticker and backfill missing fields`() {
    val barePosition =
      listOf(
        HoldingData(
          name = "Micron Technology",
          ticker = null,
          sector = null,
          weight = BigDecimal("10.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingService.saveHoldings("IITU", testDate, barePosition)
    val originalId = etfHoldingRepository.findAll().first().id
    every { holdingIdentityService.isSameCompany("Micron Technology", "Micron Technology Inc", "MU") } returns true

    val richerPosition =
      listOf(
        HoldingData(
          name = "Micron Technology Inc",
          ticker = "MU",
          sector = "Digital Hardware",
          weight = BigDecimal("9.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingService.saveHoldings("IITU", testDate.plusDays(1), richerPosition)

    val reusedHolding = etfHoldingRepository.findAll().single()
    expect(reusedHolding.id).toEqual(originalId)
    expect(reusedHolding.ticker).toEqual("MU")
    expect(reusedHolding.sector).toEqual(IndustrySector.DIGITAL_HARDWARE)
  }

  @Test
  fun `should attach position to exact name row instead of fuzzy sibling when both already exist`() {
    val nvidia =
      listOf(
        HoldingData(name = "NVIDIA", ticker = null, sector = null, weight = BigDecimal("10.0"), rank = 1, logoUrl = null),
      )
    etfHoldingService.saveHoldings("IITU", testDate, nvidia)
    val nvidiaCorp =
      listOf(
        HoldingData(name = "NVIDIA CORP", ticker = null, sector = null, weight = BigDecimal("9.0"), rank = 1, logoUrl = null),
      )
    etfHoldingService.saveHoldings("IITU", testDate.plusDays(1), nvidiaCorp)
    val corpId = etfHoldingRepository.findAll().first { it.name == "NVIDIA CORP" }.id
    every { holdingIdentityService.isSameCompany("NVIDIA", "NVIDIA CORP", null) } returns true

    etfHoldingService.saveHoldings("IITU", testDate.plusDays(2), nvidiaCorp)

    val exactRowPosition =
      etfPositionRepository.findByEtfInstrumentAndHoldingIdAndSnapshotDate(testEtf, corpId, testDate.plusDays(2))
    expect(etfHoldingRepository.findAll().size).toEqual(2)
    expect(exactRowPosition).notToEqualNull()
  }

  @Test
  fun `should not collapse two distinct share classes onto one position within a single snapshot`() {
    val legacy =
      listOf(
        HoldingData(name = "Alphabet", ticker = null, sector = null, weight = BigDecimal("10.0"), rank = 1, logoUrl = null),
      )
    etfHoldingService.saveHoldings("IITU", testDate, legacy)
    every { holdingIdentityService.isSameCompany("Alphabet", "Alphabet Class A", "GOOGL") } returns true
    every { holdingIdentityService.isSameCompany("Alphabet", "Alphabet Class C", "GOOG") } returns true

    val shareClasses =
      listOf(
        HoldingData(name = "Alphabet Class A", ticker = "GOOGL", sector = null, weight = BigDecimal("6.0"), rank = 1, logoUrl = null),
        HoldingData(name = "Alphabet Class C", ticker = "GOOG", sector = null, weight = BigDecimal("4.0"), rank = 2, logoUrl = null),
      )
    etfHoldingService.saveHoldings("IITU", testDate.plusDays(1), shareClasses)

    val snapshotPositions = etfPositionRepository.findAll().filter { it.snapshotDate == testDate.plusDays(1) }
    expect(snapshotPositions).toHaveSize(2)
  }

  @Test
  fun `should reuse existing holding when name and ticker match exactly`() {
    val holdings =
      listOf(
        HoldingData(
          name = "Apple Inc",
          ticker = "AAPL",
          sector = "Technology",
          weight = BigDecimal("15.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingService.saveHoldings("IITU", testDate, holdings)
    val originalId = etfHoldingRepository.findAll().first().id

    etfHoldingService.saveHoldings("IITU", testDate.plusDays(1), holdings)

    val allHoldings = etfHoldingRepository.findAll()
    expect(allHoldings.size).toEqual(1)
    expect(allHoldings.first().id).toEqual(originalId)
  }

  @Test
  fun `should handle multiple holdings in single save`() {
    val holdings =
      listOf(
        HoldingData(
          name = "NVIDIA Corp",
          ticker = "NVDA",
          sector = "Technology",
          weight = BigDecimal("15.0"),
          rank = 1,
          logoUrl = null,
        ),
        HoldingData(
          name = "Amazon.com Inc",
          ticker = "AMZN",
          sector = "Consumer Cyclical",
          weight = BigDecimal("12.0"),
          rank = 2,
          logoUrl = null,
        ),
        HoldingData(
          name = "Meta Platforms Inc",
          ticker = "META",
          sector = "Technology",
          weight = BigDecimal("8.0"),
          rank = 3,
          logoUrl = null,
        ),
      )

    etfHoldingService.saveHoldings("IITU", testDate, holdings)

    val savedHoldings = etfHoldingRepository.findAll()
    expect(savedHoldings.size).toEqual(3)
    expect(savedHoldings.map { it.ticker }.toSet()).toEqual(setOf("NVDA", "AMZN", "META"))
  }

  @Test
  fun `should update ticker when existing holding has no ticker`() {
    val holdingsWithoutTicker =
      listOf(
        HoldingData(
          name = "Tesla Inc",
          ticker = null,
          sector = "Automotive",
          weight = BigDecimal("5.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingService.saveHoldings("IITU", testDate, holdingsWithoutTicker)
    expect(etfHoldingRepository.findAll().first().ticker).toEqual(null)

    val holdingsWithTicker =
      listOf(
        HoldingData(
          name = "Tesla Inc",
          ticker = "TSLA",
          sector = "Automotive",
          weight = BigDecimal("5.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingService.saveHoldings("IITU", testDate.plusDays(1), holdingsWithTicker)

    val updatedHolding = etfHoldingRepository.findAll().first()
    expect(updatedHolding.ticker).toEqual("TSLA")
  }
}
