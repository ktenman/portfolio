package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class EtfBreakdownServiceTest {
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val etfPositionRepository = mockk<EtfPositionRepository>()
  private val transactionRepository = mockk<PortfolioTransactionRepository>()
  private val dailyPriceService = mockk<DailyPriceService>()
  private val cacheInvalidationService = mockk<CacheInvalidationService>(relaxed = true)
  private lateinit var etfBreakdownService: EtfBreakdownService

  @BeforeEach
  fun setup() {
    etfBreakdownService =
      EtfBreakdownService(
        instrumentRepository,
        etfPositionRepository,
        transactionRepository,
        dailyPriceService,
        cacheInvalidationService,
      )
  }

  @Test
  fun `should deduplicate holdings by ticker and prefer longer name`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR)
    val etf2 = createInstrument(2L, "ETF2", ProviderName.LIGHTYEAR)
    val holdingNvidia1 = createHolding(1L, "NVDA", "NVIDIA", "Semiconductors")
    val holdingNvidia2 = createHolding(2L, "NVDA", "Nvidia Corp", "Semiconductors")
    val position1 =
      createPosition(etf1, holdingNvidia1, BigDecimal("10.0000"), LocalDate.now())
    val position2 =
      createPosition(etf2, holdingNvidia2, BigDecimal("5.0000"), LocalDate.now())
    val transaction1 = createTransaction(etf1, BigDecimal("10"), BigDecimal("100"))
    val transaction2 = createTransaction(etf2, BigDecimal("5"), BigDecimal("200"))
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1, etf2)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position1)
    every { etfPositionRepository.findLatestPositionsByEtfId(2L) } returns listOf(position2)
    every { transactionRepository.findAllByInstrumentId(1L) } returns listOf(transaction1)
    every { transactionRepository.findAllByInstrumentId(2L) } returns listOf(transaction2)

    val result = etfBreakdownService.getHoldingsBreakdown()

    expect(result).toHaveSize(1)
    expect(result[0].holdingTicker).toEqual("NVDA")
    expect(result[0].holdingName).toEqual("Nvidia Corp")
    expect(result[0].inEtfs).toContain("ETF1")
    expect(result[0].inEtfs).toContain("ETF2")
    expect(result[0].numEtfs).toEqual(2)
    expect(result[0].platforms).toEqual("LIGHTYEAR")
  }

  @Test
  fun `should aggregate values for holdings with same ticker`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val etf2 = createInstrument(2L, "ETF2", ProviderName.LIGHTYEAR, BigDecimal("200"))
    val holdingApple1 = createHolding(1L, "AAPL", "Apple", "Technology")
    val holdingApple2 = createHolding(2L, "AAPL", "Apple Inc", "Technology")
    val position1 =
      createPosition(etf1, holdingApple1, BigDecimal("20.0000"), LocalDate.now())
    val position2 =
      createPosition(etf2, holdingApple2, BigDecimal("10.0000"), LocalDate.now())
    val transaction1 = createTransaction(etf1, BigDecimal("10"), BigDecimal("100"))
    val transaction2 = createTransaction(etf2, BigDecimal("5"), BigDecimal("200"))
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1, etf2)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position1)
    every { etfPositionRepository.findLatestPositionsByEtfId(2L) } returns listOf(position2)
    every { transactionRepository.findAllByInstrumentId(1L) } returns listOf(transaction1)
    every { transactionRepository.findAllByInstrumentId(2L) } returns listOf(transaction2)

    val result = etfBreakdownService.getHoldingsBreakdown()

    expect(result).toHaveSize(1)
    expect(result[0].holdingName).toEqual("Apple Inc")
    expect(result[0].percentageOfTotal).toEqualNumerically(BigDecimal("100.0000"))
  }

  @Test
  fun `should handle holdings without ticker using name and sector`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holdingNoTicker1 = createHolding(1L, null, "Unknown Company", "Finance")
    val holdingNoTicker2 = createHolding(2L, null, "unknown company", "Finance")
    val position1 =
      createPosition(etf1, holdingNoTicker1, BigDecimal("50.0000"), LocalDate.now())
    val position2 =
      createPosition(etf1, holdingNoTicker2, BigDecimal("50.0000"), LocalDate.now())
    val transaction = createTransaction(etf1, BigDecimal("10"), BigDecimal("100"))
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position1, position2)
    every { transactionRepository.findAllByInstrumentId(1L) } returns listOf(transaction)

    val result = etfBreakdownService.getHoldingsBreakdown()

    expect(result).toHaveSize(1)
    expect(result[0].holdingName).toEqual("Unknown Company")
  }

  @Test
  fun `should normalize ticker to uppercase for deduplication`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val etf2 = createInstrument(2L, "ETF2", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holdingLower = createHolding(1L, "msft", "Microsoft", "Software")
    val holdingUpper = createHolding(2L, "MSFT", "Microsoft Corp", "Software")
    val position1 =
      createPosition(etf1, holdingLower, BigDecimal("50.0000"), LocalDate.now())
    val position2 =
      createPosition(etf2, holdingUpper, BigDecimal("50.0000"), LocalDate.now())
    val transaction1 = createTransaction(etf1, BigDecimal("10"), BigDecimal("100"))
    val transaction2 = createTransaction(etf2, BigDecimal("10"), BigDecimal("100"))
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1, etf2)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position1)
    every { etfPositionRepository.findLatestPositionsByEtfId(2L) } returns listOf(position2)
    every { transactionRepository.findAllByInstrumentId(1L) } returns listOf(transaction1)
    every { transactionRepository.findAllByInstrumentId(2L) } returns listOf(transaction2)

    val result = etfBreakdownService.getHoldingsBreakdown()

    expect(result).toHaveSize(1)
    expect(result[0].holdingTicker).toEqual("MSFT")
    expect(result[0].holdingName).toEqual("Microsoft Corp")
  }

  @Test
  fun `should return empty list when no etfs found`() {
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns emptyList()
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()

    val result = etfBreakdownService.getHoldingsBreakdown()

    expect(result).toHaveSize(0)
  }

  @Test
  fun `should select longest sector name when merging`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val etf2 = createInstrument(2L, "ETF2", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holding1 = createHolding(1L, "GOOGL", "Google", "Tech")
    val holding2 = createHolding(2L, "GOOGL", "Alphabet Inc", "Software & Cloud Services")
    val position1 = createPosition(etf1, holding1, BigDecimal("50.0000"), LocalDate.now())
    val position2 = createPosition(etf2, holding2, BigDecimal("50.0000"), LocalDate.now())
    val transaction1 = createTransaction(etf1, BigDecimal("10"), BigDecimal("100"))
    val transaction2 = createTransaction(etf2, BigDecimal("10"), BigDecimal("100"))
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1, etf2)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position1)
    every { etfPositionRepository.findLatestPositionsByEtfId(2L) } returns listOf(position2)
    every { transactionRepository.findAllByInstrumentId(1L) } returns listOf(transaction1)
    every { transactionRepository.findAllByInstrumentId(2L) } returns listOf(transaction2)

    val result = etfBreakdownService.getHoldingsBreakdown()

    expect(result).toHaveSize(1)
    expect(result[0].holdingSector).toEqual("Software & Cloud Services")
  }

  @Test
  fun `should filter holdings by platform`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holding1 = createHolding(1L, "AAPL", "Apple", "Technology")
    val position1 = createPosition(etf1, holding1, BigDecimal("100.0000"), LocalDate.now())
    val transaction1 = createTransaction(etf1, BigDecimal("10"), BigDecimal("100"), Platform.LIGHTYEAR)
    val transaction2 = createTransaction(etf1, BigDecimal("5"), BigDecimal("100"), Platform.TRADING212)
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position1)
    every { transactionRepository.findAllByInstrumentId(1L) } returns listOf(transaction1, transaction2)

    val resultLightyear = etfBreakdownService.getHoldingsBreakdown(platforms = listOf("LIGHTYEAR"))

    expect(resultLightyear).toHaveSize(1)
    expect(resultLightyear[0].platforms).toEqual("LIGHTYEAR")
    expect(resultLightyear[0].totalValueEur).toEqualNumerically(BigDecimal("1000.00"))
  }

  @Test
  fun `should return all holdings when no platform filter specified`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holding1 = createHolding(1L, "AAPL", "Apple", "Technology")
    val position1 = createPosition(etf1, holding1, BigDecimal("100.0000"), LocalDate.now())
    val transaction1 = createTransaction(etf1, BigDecimal("10"), BigDecimal("100"), Platform.LIGHTYEAR)
    val transaction2 = createTransaction(etf1, BigDecimal("5"), BigDecimal("100"), Platform.TRADING212)
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position1)
    every { transactionRepository.findAllByInstrumentId(1L) } returns listOf(transaction1, transaction2)

    val result = etfBreakdownService.getHoldingsBreakdown()

    expect(result).toHaveSize(1)
    expect(result[0].platforms).toContain("LIGHTYEAR")
    expect(result[0].platforms).toContain("TRADING212")
    expect(result[0].totalValueEur).toEqualNumerically(BigDecimal("1500.00"))
  }

  @Test
  fun `should return empty list when platform has no active etf holdings`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holding1 = createHolding(1L, "AAPL", "Apple", "Technology")
    val position1 = createPosition(etf1, holding1, BigDecimal("100.0000"), LocalDate.now())
    val transaction1 = createTransaction(etf1, BigDecimal("10"), BigDecimal("100"), Platform.LIGHTYEAR)
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position1)
    every { transactionRepository.findAllByInstrumentId(1L) } returns listOf(transaction1)

    val result = etfBreakdownService.getHoldingsBreakdown(platforms = listOf("TRADING212"))

    expect(result).toHaveSize(0)
  }

  @Test
  fun `should ignore invalid platform names in filter`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holding1 = createHolding(1L, "AAPL", "Apple", "Technology")
    val position1 = createPosition(etf1, holding1, BigDecimal("100.0000"), LocalDate.now())
    val transaction1 = createTransaction(etf1, BigDecimal("10"), BigDecimal("100"), Platform.LIGHTYEAR)
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position1)
    every { transactionRepository.findAllByInstrumentId(1L) } returns listOf(transaction1)

    val result = etfBreakdownService.getHoldingsBreakdown(platforms = listOf("INVALID_PLATFORM"))

    expect(result).toHaveSize(1)
  }

  @Test
  fun `should combine etf symbols and platform filters with AND logic`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val etf2 = createInstrument(2L, "ETF2", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holding1 = createHolding(1L, "AAPL", "Apple", "Technology")
    val holding2 = createHolding(2L, "MSFT", "Microsoft", "Technology")
    val position1 = createPosition(etf1, holding1, BigDecimal("100.0000"), LocalDate.now())
    val position2 = createPosition(etf2, holding2, BigDecimal("100.0000"), LocalDate.now())
    val transaction1 = createTransaction(etf1, BigDecimal("10"), BigDecimal("100"), Platform.LIGHTYEAR)
    val transaction2 = createTransaction(etf2, BigDecimal("10"), BigDecimal("100"), Platform.TRADING212)
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1, etf2)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position1)
    every { etfPositionRepository.findLatestPositionsByEtfId(2L) } returns listOf(position2)
    every { transactionRepository.findAllByInstrumentId(1L) } returns listOf(transaction1)
    every { transactionRepository.findAllByInstrumentId(2L) } returns listOf(transaction2)

    val result =
      etfBreakdownService.getHoldingsBreakdown(
      etfSymbols = listOf("ETF1"),
      platforms = listOf("LIGHTYEAR"),
    )

    expect(result).toHaveSize(1)
    expect(result[0].holdingTicker).toEqual("AAPL")
    expect(result[0].platforms).toEqual("LIGHTYEAR")
  }

  private fun createInstrument(
    id: Long,
    symbol: String,
    providerName: ProviderName,
    currentPrice: BigDecimal = BigDecimal("100"),
  ): Instrument =
    Instrument(
      symbol = symbol,
      name = "Test $symbol",
      category = "ETF",
      baseCurrency = "EUR",
      currentPrice = currentPrice,
      providerName = providerName,
    ).apply { this.id = id }

  private fun createHolding(
    id: Long,
    ticker: String?,
    name: String,
    sector: String?,
  ): EtfHolding =
    EtfHolding(
      ticker = ticker,
      name = name,
      sector = sector,
    ).apply { this.id = id }

  private fun createPosition(
    etf: Instrument,
    holding: EtfHolding,
    weight: BigDecimal,
    date: LocalDate,
  ): EtfPosition =
    EtfPosition(
      etfInstrument = etf,
      holding = holding,
      weightPercentage = weight,
      snapshotDate = date,
    )

  private fun createTransaction(
    instrument: Instrument,
    quantity: BigDecimal,
    price: BigDecimal,
    platform: Platform = Platform.LIGHTYEAR,
  ): PortfolioTransaction =
    PortfolioTransaction(
      instrument = instrument,
      transactionType = TransactionType.BUY,
      quantity = quantity,
      price = price,
      transactionDate = LocalDate.now().minusDays(30),
      platform = platform,
    )
}
