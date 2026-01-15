package ee.tenman.portfolio.service.diversification

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.dto.AllocationDto
import ee.tenman.portfolio.dto.DiversificationCalculatorRequestDto
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class DiversificationCalculatorServiceTest {
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val etfPositionRepository = mockk<EtfPositionRepository>()
  private lateinit var service: DiversificationCalculatorService

  @BeforeEach
  fun setup() {
    service = DiversificationCalculatorService(instrumentRepository, etfPositionRepository)
  }

  @Test
  fun `should calculate weighted ter for single etf`() {
    val etf = createInstrument(1L, "VWCE", ter = BigDecimal("0.22"))
    val holding = createHolding(1L, "AAPL", "Apple Inc", "Technology", "US", "United States")
    val position = createPosition(etf, holding, BigDecimal("10.0000"))
    setupMocks(listOf(etf), listOf(position))
    val request = createRequest(AllocationDto(1L, BigDecimal("100")))

    val result = service.calculate(request)

    expect(result.weightedTer).toEqualNumerically(BigDecimal("0.2200"))
  }

  @Test
  fun `should calculate weighted ter for multiple etfs`() {
    val etf1 = createInstrument(1L, "VWCE", ter = BigDecimal("0.22"))
    val etf2 = createInstrument(2L, "VUAA", ter = BigDecimal("0.07"))
    val holding1 = createHolding(1L, "AAPL", "Apple", "Technology", "US", "United States")
    val holding2 = createHolding(2L, "MSFT", "Microsoft", "Technology", "US", "United States")
    val position1 = createPosition(etf1, holding1, BigDecimal("100.0000"))
    val position2 = createPosition(etf2, holding2, BigDecimal("100.0000"))
    setupMocks(listOf(etf1, etf2), listOf(position1, position2))
    val request =
      createRequest(
      AllocationDto(1L, BigDecimal("50")),
      AllocationDto(2L, BigDecimal("50")),
    )

    val result = service.calculate(request)

    expect(result.weightedTer).toEqualNumerically(BigDecimal("0.1450"))
  }

  @Test
  fun `should calculate weighted annual return`() {
    val etf1 = createInstrument(1L, "VWCE", annualReturn = BigDecimal("0.12"))
    val etf2 = createInstrument(2L, "VUAA", annualReturn = BigDecimal("0.15"))
    val holding1 = createHolding(1L, "AAPL", "Apple", "Technology", "US", "United States")
    val holding2 = createHolding(2L, "MSFT", "Microsoft", "Technology", "US", "United States")
    val position1 = createPosition(etf1, holding1, BigDecimal("100.0000"))
    val position2 = createPosition(etf2, holding2, BigDecimal("100.0000"))
    setupMocks(listOf(etf1, etf2), listOf(position1, position2))
    val request =
      createRequest(
      AllocationDto(1L, BigDecimal("60")),
      AllocationDto(2L, BigDecimal("40")),
    )

    val result = service.calculate(request)

    expect(result.weightedAnnualReturn).toEqualNumerically(BigDecimal("0.1320"))
  }

  @Test
  fun `should aggregate holdings from multiple etfs`() {
    val etf1 = createInstrument(1L, "VWCE")
    val etf2 = createInstrument(2L, "VUAA")
    val appleHolding1 = createHolding(1L, "AAPL", "Apple Inc", "Technology", "US", "United States")
    val appleHolding2 = createHolding(2L, "AAPL", "Apple Inc", "Technology", "US", "United States")
    val position1 = createPosition(etf1, appleHolding1, BigDecimal("5.0000"))
    val position2 = createPosition(etf2, appleHolding2, BigDecimal("10.0000"))
    setupMocks(listOf(etf1, etf2), listOf(position1, position2))
    val request =
      createRequest(
      AllocationDto(1L, BigDecimal("50")),
      AllocationDto(2L, BigDecimal("50")),
    )

    val result = service.calculate(request)

    expect(result.holdings).toHaveSize(1)
    expect(result.holdings[0].name).toEqual("Apple Inc")
    expect(result.holdings[0].percentage).toEqualNumerically(BigDecimal("7.5000"))
    expect(result.holdings[0].inEtfs).toContain("VWCE")
    expect(result.holdings[0].inEtfs).toContain("VUAA")
  }

  @Test
  fun `should aggregate sectors correctly`() {
    val etf = createInstrument(1L, "VWCE")
    val holding1 = createHolding(1L, "AAPL", "Apple", "Technology", "US", "United States")
    val holding2 = createHolding(2L, "MSFT", "Microsoft", "Technology", "US", "United States")
    val holding3 = createHolding(3L, "JPM", "JPMorgan", "Financials", "US", "United States")
    val position1 = createPosition(etf, holding1, BigDecimal("30.0000"))
    val position2 = createPosition(etf, holding2, BigDecimal("20.0000"))
    val position3 = createPosition(etf, holding3, BigDecimal("10.0000"))
    setupMocks(listOf(etf), listOf(position1, position2, position3))
    val request = createRequest(AllocationDto(1L, BigDecimal("100")))

    val result = service.calculate(request)

    expect(result.sectors).toHaveSize(2)
    val techSector = result.sectors.find { it.sector == "Technology" }
    expect(techSector).notToEqualNull()
    expect(techSector!!.percentage).toEqualNumerically(BigDecimal("50.0000"))
  }

  @Test
  fun `should aggregate countries correctly`() {
    val etf = createInstrument(1L, "VWCE")
    val holding1 = createHolding(1L, "AAPL", "Apple", "Technology", "US", "United States")
    val holding2 = createHolding(2L, "ASML", "ASML", "Technology", "NL", "Netherlands")
    val position1 = createPosition(etf, holding1, BigDecimal("60.0000"))
    val position2 = createPosition(etf, holding2, BigDecimal("40.0000"))
    setupMocks(listOf(etf), listOf(position1, position2))
    val request = createRequest(AllocationDto(1L, BigDecimal("100")))

    val result = service.calculate(request)

    expect(result.countries).toHaveSize(2)
    val usCountry = result.countries.find { it.countryName == "United States" }
    expect(usCountry).notToEqualNull()
    expect(usCountry!!.percentage).toEqualNumerically(BigDecimal("60.0000"))
  }

  @Test
  fun `should calculate top 10 concentration`() {
    val etf = createInstrument(1L, "VWCE")
    val holdings =
      (1..15).map { i ->
      createHolding(i.toLong(), "TICK$i", "Company $i", "Technology", "US", "United States")
    }
    val positions =
      holdings.mapIndexed { index, holding ->
      createPosition(etf, holding, BigDecimal(20 - index))
    }
    setupMocks(listOf(etf), positions)
    val request = createRequest(AllocationDto(1L, BigDecimal("100")))

    val result = service.calculate(request)

    expect(result.concentration.top10Percentage).toEqualNumerically(BigDecimal("155.0000"))
    expect(result.concentration.largestPosition).notToEqualNull()
    expect(result.concentration.largestPosition!!.name).toEqual("Company 1")
  }

  @Test
  fun `should normalize allocations to 100 percent`() {
    val etf1 = createInstrument(1L, "VWCE", ter = BigDecimal("0.22"))
    val etf2 = createInstrument(2L, "VUAA", ter = BigDecimal("0.07"))
    val holding1 = createHolding(1L, "AAPL", "Apple", "Technology", "US", "United States")
    val holding2 = createHolding(2L, "MSFT", "Microsoft", "Technology", "US", "United States")
    val position1 = createPosition(etf1, holding1, BigDecimal("100.0000"))
    val position2 = createPosition(etf2, holding2, BigDecimal("100.0000"))
    setupMocks(listOf(etf1, etf2), listOf(position1, position2))
    val request =
      createRequest(
      AllocationDto(1L, BigDecimal("30")),
      AllocationDto(2L, BigDecimal("70")),
    )

    val result = service.calculate(request)

    expect(result.weightedTer).toEqualNumerically(BigDecimal("0.1150"))
  }

  @Test
  fun `should return zero weighted ter when no etfs have ter`() {
    val etf = createInstrument(1L, "VWCE", ter = null)
    val holding = createHolding(1L, "AAPL", "Apple", "Technology", "US", "United States")
    val position = createPosition(etf, holding, BigDecimal("100.0000"))
    setupMocks(listOf(etf), listOf(position))
    val request = createRequest(AllocationDto(1L, BigDecimal("100")))

    val result = service.calculate(request)

    expect(result.weightedTer).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should return zero weighted annual return when no etfs have return`() {
    val etf = createInstrument(1L, "VWCE", annualReturn = null)
    val holding = createHolding(1L, "AAPL", "Apple", "Technology", "US", "United States")
    val position = createPosition(etf, holding, BigDecimal("100.0000"))
    setupMocks(listOf(etf), listOf(position))
    val request = createRequest(AllocationDto(1L, BigDecimal("100")))

    val result = service.calculate(request)

    expect(result.weightedAnnualReturn).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should throw exception for empty allocations`() {
    val request = DiversificationCalculatorRequestDto(emptyList())

    expect { service.calculate(request) }
      .toThrow<IllegalArgumentException>()
      .messageToContain("At least one valid ETF allocation is required")
  }

  @Test
  fun `should return available etfs sorted by symbol`() {
    val etf1 = createInstrument(1L, "VUAA", ter = BigDecimal("0.07"))
    val etf2 = createInstrument(2L, "VWCE", ter = BigDecimal("0.22"))
    val etf3 = createInstrument(3L, "CSPX", ter = BigDecimal("0.07"))
    every { etfPositionRepository.findDistinctEtfInstrumentIds() } returns listOf(1L, 2L, 3L)
    every { instrumentRepository.findAllById(listOf(1L, 2L, 3L)) } returns listOf(etf1, etf2, etf3)

    val result = service.getAvailableEtfs()

    expect(result).toHaveSize(3)
    expect(result[0].symbol).toEqual("CSPX")
    expect(result[1].symbol).toEqual("VUAA")
    expect(result[2].symbol).toEqual("VWCE")
  }

  @Test
  fun `should filter out non-ETF instruments from available etfs`() {
    val etf = createInstrument(1L, "VWCE", ter = BigDecimal("0.22"))
    val crypto = createInstrumentWithCategory(2L, "BTC", "Crypto")
    val cash = createInstrumentWithCategory(3L, "CASH", "Cash")
    every { etfPositionRepository.findDistinctEtfInstrumentIds() } returns listOf(1L, 2L, 3L)
    every { instrumentRepository.findAllById(listOf(1L, 2L, 3L)) } returns listOf(etf, crypto, cash)

    val result = service.getAvailableEtfs()

    expect(result).toHaveSize(1)
    expect(result[0].symbol).toEqual("VWCE")
  }

  @Test
  fun `should handle holdings with unknown sector`() {
    val etf = createInstrument(1L, "VWCE")
    val holding = createHolding(1L, "XYZ", "Unknown Corp", null, "US", "United States")
    val position = createPosition(etf, holding, BigDecimal("100.0000"))
    setupMocks(listOf(etf), listOf(position))
    val request = createRequest(AllocationDto(1L, BigDecimal("100")))

    val result = service.calculate(request)

    expect(result.sectors).toHaveSize(1)
    expect(result.sectors[0].sector).toEqual("Unknown")
  }

  @Test
  fun `should handle holdings with unknown country`() {
    val etf = createInstrument(1L, "VWCE")
    val holding = createHolding(1L, "XYZ", "Unknown Corp", "Technology", null, null)
    val position = createPosition(etf, holding, BigDecimal("100.0000"))
    setupMocks(listOf(etf), listOf(position))
    val request = createRequest(AllocationDto(1L, BigDecimal("100")))

    val result = service.calculate(request)

    expect(result.countries).toHaveSize(1)
    expect(result.countries[0].countryName).toEqual("Unknown")
  }

  @Test
  fun `should normalize holding names for deduplication`() {
    val etf1 = createInstrument(1L, "VWCE")
    val etf2 = createInstrument(2L, "VUAA")
    val holding1 = createHolding(1L, "AAPL", "Apple Inc", "Technology", "US", "United States")
    val holding2 = createHolding(2L, "AAPL", "apple inc", "Technology", "US", "United States")
    val position1 = createPosition(etf1, holding1, BigDecimal("50.0000"))
    val position2 = createPosition(etf2, holding2, BigDecimal("50.0000"))
    setupMocks(listOf(etf1, etf2), listOf(position1, position2))
    val request =
      createRequest(
      AllocationDto(1L, BigDecimal("50")),
      AllocationDto(2L, BigDecimal("50")),
    )

    val result = service.calculate(request)

    expect(result.holdings).toHaveSize(1)
  }

  @Test
  fun `should throw exception for zero total allocation`() {
    val request = createRequest(AllocationDto(1L, BigDecimal.ZERO))

    expect { service.calculate(request) }
      .toThrow<IllegalArgumentException>()
      .messageToContain("Total allocation percentage must be greater than zero")
  }

  private fun setupMocks(
    instruments: List<Instrument>,
    positions: List<EtfPosition>,
  ) {
    val ids = instruments.map { it.id }
    every { instrumentRepository.findAllById(ids) } returns instruments
    every { etfPositionRepository.findLatestPositionsByEtfIds(ids) } returns positions
  }

  private fun createRequest(vararg allocations: AllocationDto) = DiversificationCalculatorRequestDto(allocations.toList())

  private fun createInstrument(
    id: Long,
    symbol: String,
    ter: BigDecimal? = BigDecimal("0.20"),
    annualReturn: BigDecimal? = BigDecimal("0.10"),
    currentPrice: BigDecimal? = BigDecimal("100.00"),
  ): Instrument =
    Instrument(
      symbol = symbol,
      name = "Test $symbol",
      category = "ETF",
      baseCurrency = "EUR",
      currentPrice = currentPrice,
      providerName = ProviderName.LIGHTYEAR,
    ).apply {
      this.id = id
      this.ter = ter
      this.xirrAnnualReturn = annualReturn
    }

  private fun createInstrumentWithCategory(
    id: Long,
    symbol: String,
    category: String,
  ): Instrument =
    Instrument(
      symbol = symbol,
      name = "Test $symbol",
      category = category,
      baseCurrency = "EUR",
      currentPrice = BigDecimal("100.00"),
      providerName = ProviderName.LIGHTYEAR,
    ).apply { this.id = id }

  private fun createHolding(
    id: Long,
    ticker: String?,
    name: String,
    sector: String?,
    countryCode: String?,
    countryName: String?,
  ): EtfHolding =
    EtfHolding(
      ticker = ticker,
      name = name,
      sector = sector,
      countryCode = countryCode,
      countryName = countryName,
    ).apply { this.id = id }

  private fun createPosition(
    etf: Instrument,
    holding: EtfHolding,
    weight: BigDecimal,
  ): EtfPosition =
    EtfPosition(
      etfInstrument = etf,
      holding = holding,
      weightPercentage = weight,
      snapshotDate = SNAPSHOT_DATE,
    )

  companion object {
    private val SNAPSHOT_DATE = LocalDate.of(2024, 1, 15)
  }
}
