package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@IntegrationTest
class EtfHoldingPersistenceServiceIntegrationTest {
  @Resource
  private lateinit var etfHoldingPersistenceService: EtfHoldingPersistenceService

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var etfHoldingRepository: EtfHoldingRepository

  @Resource
  private lateinit var etfPositionRepository: EtfPositionRepository

  private lateinit var etfInstrument: Instrument

  @BeforeEach
  fun setUp() {
    etfInstrument =
      instrumentRepository.save(
      Instrument(
        symbol = "VWCE",
        name = "Vanguard FTSE All-World",
        category = "ETF",
        baseCurrency = "EUR",
        currentPrice = BigDecimal("100.00"),
        providerName = ProviderName.LIGHTYEAR,
      ),
    )
  }

  @Test
  fun `should saveHoldings create new holdings and positions`() {
    val date = LocalDate.of(2024, 7, 1)
    val holdings =
      listOf(
      HoldingData(
        name = "Apple Inc",
        ticker = "AAPL",
        sector = "Technology",
        weight = BigDecimal("5.50"),
        rank = 1,
      ),
      HoldingData(
        name = "Microsoft Corp",
        ticker = "MSFT",
        sector = "Technology",
        weight = BigDecimal("4.80"),
        rank = 2,
      ),
    )

    val result = etfHoldingPersistenceService.saveHoldings("VWCE", date, holdings)

    expect(result.keys).toContainExactly("Apple Inc", "Microsoft Corp")
    val savedHoldings = etfHoldingRepository.findAll()
    expect(savedHoldings).toHaveSize(2)
    val positions = etfPositionRepository.findAll()
    expect(positions).toHaveSize(2)
    val applePosition = positions.first { it.holding.name == "Apple Inc" }
    expect(applePosition.weightPercentage).toEqualNumerically(BigDecimal("5.50"))
    expect(applePosition.positionRank).toEqual(1)
  }

  @Test
  fun `should saveHoldings update existing positions on same date`() {
    val date = LocalDate.of(2024, 7, 1)
    val initialHoldings =
      listOf(
      HoldingData(
        name = "Apple Inc",
        ticker = "AAPL",
        sector = "Technology",
        weight = BigDecimal("5.50"),
        rank = 1,
      ),
    )
    etfHoldingPersistenceService.saveHoldings("VWCE", date, initialHoldings)
    val updatedHoldings =
      listOf(
      HoldingData(
        name = "Apple Inc",
        ticker = "AAPL",
        sector = "Technology",
        weight = BigDecimal("6.00"),
        rank = 1,
      ),
    )

    etfHoldingPersistenceService.saveHoldings("VWCE", date, updatedHoldings)

    val positions = etfPositionRepository.findAll()
    expect(positions).toHaveSize(1)
    expect(positions.first().weightPercentage).toEqualNumerically(BigDecimal("6.00"))
  }

  @Test
  fun `should saveHoldings handle mixed new and existing holdings`() {
    val date = LocalDate.of(2024, 7, 1)
    val initialHoldings =
      listOf(
      HoldingData(
        name = "Apple Inc",
        ticker = "AAPL",
        sector = "Technology",
        weight = BigDecimal("5.50"),
        rank = 1,
      ),
    )
    etfHoldingPersistenceService.saveHoldings("VWCE", date, initialHoldings)
    val mixedHoldings =
      listOf(
      HoldingData(
        name = "Apple Inc",
        ticker = "AAPL",
        sector = "Technology",
        weight = BigDecimal("5.00"),
        rank = 2,
      ),
      HoldingData(
        name = "Amazon.com Inc",
        ticker = "AMZN",
        sector = "Consumer Discretionary",
        weight = BigDecimal("3.50"),
        rank = 1,
      ),
    )

    val result = etfHoldingPersistenceService.saveHoldings("VWCE", date, mixedHoldings)

    expect(result.keys).toContainExactly("Apple Inc", "Amazon.com Inc")
    val holdings = etfHoldingRepository.findAll()
    expect(holdings).toHaveSize(2)
    val positions = etfPositionRepository.findAll()
    expect(positions).toHaveSize(2)
  }

  @Test
  fun `should findOrCreateHolding return existing holding`() {
    etfHoldingPersistenceService.findOrCreateHolding("Apple Inc", "AAPL", "Technology")

    val result = etfHoldingPersistenceService.findOrCreateHolding("Apple Inc", "AAPL", "Technology")

    val holdings = etfHoldingRepository.findAll()
    expect(holdings).toHaveSize(1)
    expect(result.name).toEqual("Apple Inc")
  }

  @Test
  fun `should findOrCreateHolding create new holding when not exists`() {
    val result = etfHoldingPersistenceService.findOrCreateHolding("Apple Inc", "AAPL", "Technology")

    expect(result.id).toBeGreaterThan(0L)
    expect(result.name).toEqual("Apple Inc")
    expect(result.ticker).toEqual("AAPL")
    expect(result.sector).toEqual("Technology")
    val holdings = etfHoldingRepository.findAll()
    expect(holdings).toHaveSize(1)
  }

  @Test
  fun `should findOrCreateHolding be case insensitive`() {
    etfHoldingPersistenceService.findOrCreateHolding("Apple Inc", "AAPL", "Technology")

    val result = etfHoldingPersistenceService.findOrCreateHolding("APPLE INC", "AAPL", "Technology")

    val holdings = etfHoldingRepository.findAll()
    expect(holdings).toHaveSize(1)
    expect(result.name).toEqual("Apple Inc")
  }

  @Test
  fun `should saveHoldings handle positions on different dates`() {
    val date1 = LocalDate.of(2024, 7, 1)
    val date2 = LocalDate.of(2024, 7, 2)
    val holdings =
      listOf(
      HoldingData(
        name = "Apple Inc",
        ticker = "AAPL",
        sector = "Technology",
        weight = BigDecimal("5.50"),
        rank = 1,
      ),
    )

    etfHoldingPersistenceService.saveHoldings("VWCE", date1, holdings)
    etfHoldingPersistenceService.saveHoldings("VWCE", date2, holdings)

    val allHoldings = etfHoldingRepository.findAll()
    expect(allHoldings).toHaveSize(1)
    val positions = etfPositionRepository.findAll()
    expect(positions).toHaveSize(2)
  }

  @Test
  fun `should hasHoldingsForDate return true when holdings exist`() {
    val date = LocalDate.of(2024, 7, 1)
    val holdings =
      listOf(
      HoldingData(
        name = "Apple Inc",
        ticker = "AAPL",
        sector = "Technology",
        weight = BigDecimal("5.50"),
        rank = 1,
      ),
    )
    etfHoldingPersistenceService.saveHoldings("VWCE", date, holdings)

    val result = etfHoldingPersistenceService.hasHoldingsForDate("VWCE", date)

    expect(result).toEqual(true)
  }

  @Test
  fun `should hasHoldingsForDate return false when no holdings exist`() {
    val date = LocalDate.of(2024, 7, 1)

    val result = etfHoldingPersistenceService.hasHoldingsForDate("VWCE", date)

    expect(result).toEqual(false)
  }

  @Test
  fun `should updateSector update holding sector`() {
    val holding = etfHoldingPersistenceService.findOrCreateHolding("Apple Inc", "AAPL", null)

    etfHoldingPersistenceService.updateSector(holding.id, "Information Technology")

    val updated = etfHoldingRepository.findById(holding.id).orElseThrow()
    expect(updated.sector).toEqual("Information Technology")
  }

  @Test
  fun `should saveHoldings handle large number of holdings`() {
    val date = LocalDate.of(2024, 7, 1)
    val holdings =
      (1..50).map { i ->
      HoldingData(
        name = "Company $i",
        ticker = "TICK$i",
        sector = "Technology",
        weight = BigDecimal("2.00"),
        rank = i,
      )
    }

    val result = etfHoldingPersistenceService.saveHoldings("VWCE", date, holdings)

    expect(result.size).toEqual(50)
    val savedHoldings = etfHoldingRepository.findAll()
    expect(savedHoldings).toHaveSize(50)
    val positions = etfPositionRepository.findAll()
    expect(positions).toHaveSize(50)
  }
}
