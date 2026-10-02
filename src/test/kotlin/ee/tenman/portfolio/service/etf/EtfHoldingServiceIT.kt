package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import com.ninjasquad.springmockk.MockkBean
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.IndustrySector
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.SectorSource
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.dto.IdentityPair
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.testing.fixture.answerPairs
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
    holdingIdentityService.answerPairs { null }
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
          sectorSource = SectorSource.LIGHTYEAR,
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
          sectorSource = SectorSource.LIGHTYEAR,
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
          sectorSource = SectorSource.LIGHTYEAR,
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
    holdingIdentityService.answerPairs(mapOf(IdentityPair("Amazon", "Amazon.com Inc", "AMZN", null, null, "AMZN") to true)::get)

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
    holdingIdentityService.answerPairs(mapOf(IdentityPair("Amazon", "Amazon.com Inc", "AMZN") to null)::get)

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
    holdingIdentityService.answerPairs(mapOf(IdentityPair("Micron Technology", "Micron Technology Inc", "MU") to true)::get)

    val richerPosition =
      listOf(
        HoldingData(
          name = "Micron Technology Inc",
          ticker = "MU",
          sector = "Digital Hardware",
          weight = BigDecimal("9.0"),
          rank = 1,
          logoUrl = null,
          sectorSource = SectorSource.LIGHTYEAR,
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
    holdingIdentityService.answerPairs(mapOf(IdentityPair("NVIDIA", "NVIDIA CORP", null) to true)::get)

    etfHoldingService.saveHoldings("IITU", testDate.plusDays(2), nvidiaCorp)

    val exactRowPosition =
      etfPositionRepository.findByEtfInstrumentAndHoldingIdAndSnapshotDate(testEtf, corpId, testDate.plusDays(2))
    expect(etfHoldingRepository.findAll().size).toEqual(2)
    expect(exactRowPosition).notToEqualNull()
  }

  @Test
  fun `should sum share classes confirmed as one issuer into a single position`() {
    etfHoldingService.saveHoldings("IITU", testDate, listOf(row("Alphabet", null, "10.0", 1)))
    holdingIdentityService.answerPairs { true }
    val shareClasses = listOf(row("Alphabet Class A", "GOOGL", "6.0", 1), row("Alphabet Class C", "GOOG", "4.0", 2))
    etfHoldingService.saveHoldings("IITU", testDate.plusDays(1), shareClasses)
    expect(weightsOn(testDate.plusDays(1))).toContainExactly(BigDecimal("10.0"))
  }

  @Test
  fun `should sum a hinted share class with a later row carrying the holding name`() {
    etfHoldingService.saveHoldings("IITU", testDate, listOf(row("Alphabet", null, "10.0", 1)))
    holdingIdentityService.answerPairs { true }
    val feed = listOf(row("Alphabet Class A", "GOOGL", "6.0", 1), row("Alphabet", null, "4.0", 2))
    etfHoldingService.saveHoldings("IITU", testDate.plusDays(1), feed)
    expect(weightsOn(testDate.plusDays(1))).toContainExactly(BigDecimal("10.0"))
  }

  @Test
  fun `should sum a hinted share class with an earlier row carrying the holding name`() {
    etfHoldingService.saveHoldings("IITU", testDate, listOf(row("Alphabet", null, "10.0", 1)))
    holdingIdentityService.answerPairs { true }
    val feed = listOf(row("Alphabet", null, "4.0", 1), row("Alphabet Class A", "GOOGL", "6.0", 2))
    etfHoldingService.saveHoldings("IITU", testDate.plusDays(1), feed)
    expect(weightsOn(testDate.plusDays(1))).toContainExactly(BigDecimal("10.0"))
  }

  @Test
  fun `should sum rows repeating one name and keep the lowest rank`() {
    val feed = listOf(row("Škoda Auto", "SKODA", "2.5", 3), row("Škoda Auto", "SKODA", "1.5", 7))
    etfHoldingService.saveHoldings("IITU", testDate, feed)
    val position = etfPositionRepository.findAll().single()
    expect(position.weightPercentage.compareTo(BigDecimal("4.0")) to position.positionRank).toEqual(0 to 3)
  }

  @Test
  fun `should replace summed weights when the same snapshot is saved twice`() {
    val feed = listOf(row("Škoda Auto", "SKODA", "2.5", 1), row("Škoda Auto", "SKODA", "1.5", 2))
    etfHoldingService.saveHoldings("IITU", testDate, feed)
    etfHoldingService.saveHoldings("IITU", testDate, feed)
    expect(weightsOn(testDate)).toContainExactly(BigDecimal("4.0"))
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

  @Test
  fun `should keep one position per holding when the same snapshot is saved twice`() {
    val holdings = listOf(HoldingData(name = "Škoda Auto", ticker = "SKODA", sector = null, weight = BigDecimal("2.50"), rank = 1))
    etfHoldingService.saveHoldings("IITU", testDate, holdings)
    etfHoldingService.saveHoldings("IITU", testDate, holdings)
    expect(etfPositionRepository.findAll()).toHaveSize(1)
  }

  private fun weightsOn(date: LocalDate): List<BigDecimal> =
    etfPositionRepository.findAll().filter { it.snapshotDate == date }.map { it.weightPercentage.setScale(1) }

  private fun row(
    name: String,
    ticker: String?,
    weight: String,
    rank: Int,
  ) = HoldingData(name = name, ticker = ticker, sector = null, weight = BigDecimal(weight), rank = rank)
}
