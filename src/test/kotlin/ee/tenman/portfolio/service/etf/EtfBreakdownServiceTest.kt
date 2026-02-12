package ee.tenman.portfolio.service.etf

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
import ee.tenman.portfolio.service.infrastructure.CacheInvalidationService
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.transaction.TransactionCalculationService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.domain.Specification
import java.math.BigDecimal
import java.time.LocalDate

class EtfBreakdownServiceTest {
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val etfPositionRepository = mockk<EtfPositionRepository>()
  private val transactionRepository = mockk<PortfolioTransactionRepository>()
  private val dailyPriceService = mockk<DailyPriceService>()
  private val cacheInvalidationService = mockk<CacheInvalidationService>(relaxed = true)
  private val holdingAggregationService = HoldingAggregationService()
  private val transactionCalculationService = TransactionCalculationService(transactionRepository)
  private val syntheticEtfCalculationService =
    SyntheticEtfCalculationService(
      instrumentRepository,
      etfPositionRepository,
      transactionCalculationService,
      dailyPriceService,
    )
  private val dataLoader = EtfBreakdownDataLoaderService(instrumentRepository, etfPositionRepository, transactionCalculationService)
  private val testDate = LocalDate.of(2024, 1, 15)
  private lateinit var etfBreakdownService: EtfBreakdownService

  @BeforeEach
  fun setup() {
    etfBreakdownService =
      EtfBreakdownService(
        dailyPriceService,
        cacheInvalidationService,
        holdingAggregationService,
        syntheticEtfCalculationService,
        transactionCalculationService,
        dataLoader,
      )
  }

  @Test
  fun `should deduplicate holdings by name and sector with different tickers`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR)
    val etf2 = createInstrument(2L, "ETF2", ProviderName.LIGHTYEAR)
    val holdingNvidia1 = createHolding(1L, "NVDA", "Nvidia", "Semiconductors")
    val holdingNvidia2 = createHolding(2L, "NVD", "Nvidia", "Semiconductors")
    val position1 = createPosition(etf1, holdingNvidia1, BigDecimal("10.0000"), testDate)
    val position2 = createPosition(etf2, holdingNvidia2, BigDecimal("5.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"))
    val transaction2 = createCashFlow(etf2, BigDecimal("5"), BigDecimal("200"))
    setupMocksForBatchLoading(listOf(etf1, etf2), listOf(position1, position2), listOf(transaction1, transaction2))

    val result = etfBreakdownService.getHoldingsBreakdown()

    expect(result).toHaveSize(1)
    expect(result[0].holdingTicker).toEqual("NVDA")
    expect(result[0].holdingName).toEqual("Nvidia")
    expect(result[0].inEtfs).toContain("ETF1")
    expect(result[0].inEtfs).toContain("ETF2")
    expect(result[0].numEtfs).toEqual(2)
    expect(result[0].platforms).toEqual("LIGHTYEAR")
  }

  @Test
  fun `should aggregate values for holdings with same name and sector`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val etf2 = createInstrument(2L, "ETF2", ProviderName.LIGHTYEAR, BigDecimal("200"))
    val holdingApple1 = createHolding(1L, "AAPL", "Apple", "Technology")
    val holdingApple2 = createHolding(2L, "APL", "Apple", "Technology")
    val position1 = createPosition(etf1, holdingApple1, BigDecimal("20.0000"), testDate)
    val position2 = createPosition(etf2, holdingApple2, BigDecimal("10.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"))
    val transaction2 = createCashFlow(etf2, BigDecimal("5"), BigDecimal("200"))
    setupMocksForBatchLoading(listOf(etf1, etf2), listOf(position1, position2), listOf(transaction1, transaction2))

    val result = etfBreakdownService.getHoldingsBreakdown()

    expect(result).toHaveSize(1)
    expect(result[0].holdingName).toEqual("Apple")
    expect(result[0].percentageOfTotal).toEqualNumerically(BigDecimal("100.0000"))
  }

  @Test
  fun `should handle holdings without ticker using name`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holdingNoTicker1 = createHolding(1L, null, "Unknown Financial", "Finance")
    val holdingNoTicker2 = createHolding(2L, null, "unknown financial", "Finance")
    val position1 = createPosition(etf1, holdingNoTicker1, BigDecimal("50.0000"), testDate)
    val position2 = createPosition(etf1, holdingNoTicker2, BigDecimal("50.0000"), testDate)
    val transaction = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"))
    setupMocksForBatchLoading(listOf(etf1), listOf(position1, position2), listOf(transaction))

    val result = etfBreakdownService.getHoldingsBreakdown()

    expect(result).toHaveSize(1)
    expect(result[0].holdingName).toEqual("Unknown Financial")
  }

  @Test
  fun `should merge holdings with same name in different cases and prefer longer ticker`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val etf2 = createInstrument(2L, "ETF2", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holdingLower = createHolding(1L, "ms", "microsoft", "Software")
    val holdingUpper = createHolding(2L, "MSFT", "MICROSOFT", "Software")
    val position1 = createPosition(etf1, holdingLower, BigDecimal("50.0000"), testDate)
    val position2 = createPosition(etf2, holdingUpper, BigDecimal("50.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"))
    val transaction2 = createCashFlow(etf2, BigDecimal("10"), BigDecimal("100"))
    setupMocksForBatchLoading(listOf(etf1, etf2), listOf(position1, position2), listOf(transaction1, transaction2))

    val result = etfBreakdownService.getHoldingsBreakdown()

    expect(result).toHaveSize(1)
    expect(result[0].holdingTicker).toEqual("MSFT")
  }

  @Test
  fun `should return empty list when no etfs found`() {
    every { instrumentRepository.findAll(any<Specification<Instrument>>()) } returns emptyList()
    every { etfPositionRepository.findLatestPositionsByEtfIds(any()) } returns emptyList()

    val result = etfBreakdownService.getHoldingsBreakdown()

    expect(result).toHaveSize(0)
  }

  @Test
  fun `should select longest sector name when merging by name`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val etf2 = createInstrument(2L, "ETF2", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holding1 = createHolding(1L, "GOOGL", "Google", "Tech")
    val holding2 = createHolding(2L, "GOOGL", "Google", "Software & Cloud Services")
    val position1 = createPosition(etf1, holding1, BigDecimal("50.0000"), testDate)
    val position2 = createPosition(etf2, holding2, BigDecimal("50.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"))
    val transaction2 = createCashFlow(etf2, BigDecimal("10"), BigDecimal("100"))
    setupMocksForBatchLoading(listOf(etf1, etf2), listOf(position1, position2), listOf(transaction1, transaction2))

    val result = etfBreakdownService.getHoldingsBreakdown()

    expect(result).toHaveSize(1)
    expect(result[0].holdingSector).toEqual("Software & Cloud Services")
  }

  @Test
  fun `should merge holdings with same name from different ETFs`() {
    val etf1 = createInstrument(1L, "WBIT", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val etf2 = createInstrument(2L, "TREZOR", ProviderName.SYNTHETIC, BigDecimal("100"))
    val btcInstrument = createInstrument(3L, "BTCEUR", ProviderName.BINANCE, BigDecimal("50000"))
    val holdingBitcoin = createHolding(1L, "BTC", "Bitcoin", "Cryptocurrency")
    val holdingBitcoinSynthetic = createHolding(2L, "BTCEUR", "Bitcoin", "Cryptocurrency")
    val position1 = createPosition(etf1, holdingBitcoin, BigDecimal("50.0000"), testDate)
    val position2 = createPosition(etf2, holdingBitcoinSynthetic, BigDecimal("50.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"))
    val btcTransaction = createCashFlow(btcInstrument, BigDecimal("1"), BigDecimal("50000"))
    every { instrumentRepository.findAll(any<Specification<Instrument>>()) } returns listOf(etf1, etf2)
    every { etfPositionRepository.findLatestPositionsByEtfIds(any()) } returns listOf(position1, position2)
    every { etfPositionRepository.findLatestPositionsByEtfId(2L) } returns listOf(position2)
    every { transactionRepository.findAllByInstrumentIds(listOf(1L, 2L)) } returns listOf(transaction1)
    every { transactionRepository.findAllByInstrumentIds(listOf(3L)) } returns listOf(btcTransaction)
    every { instrumentRepository.findBySymbolIn(listOf("BTCEUR")) } returns listOf(btcInstrument)
    every { dailyPriceService.getCurrentPrice(any()) } answers {
      val instrument = firstArg<Instrument>()
      instrument.currentPrice ?: BigDecimal("100")
    }

    val result = etfBreakdownService.getHoldingsBreakdown()

    expect(result).toHaveSize(1)
    expect(result[0].holdingName).toEqual("Bitcoin")
    expect(result[0].holdingTicker).toEqual("BTCEUR")
    expect(result[0].inEtfs).toContain("WBIT")
    expect(result[0].inEtfs).toContain("TREZOR")
    expect(result[0].numEtfs).toEqual(2)
  }

  @Test
  fun `should filter holdings by platform`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holding1 = createHolding(1L, "AAPL", "Apple", "Technology")
    val position1 = createPosition(etf1, holding1, BigDecimal("100.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"), Platform.LIGHTYEAR)
    val transaction2 = createCashFlow(etf1, BigDecimal("5"), BigDecimal("100"), Platform.TRADING212)
    setupMocksForBatchLoading(listOf(etf1), listOf(position1), listOf(transaction1, transaction2))

    val resultLightyear = etfBreakdownService.getHoldingsBreakdown(platforms = listOf("LIGHTYEAR"))

    expect(resultLightyear).toHaveSize(1)
    expect(resultLightyear[0].platforms).toEqual("LIGHTYEAR")
    expect(resultLightyear[0].totalValueEur).toEqualNumerically(BigDecimal("1000.00"))
  }

  @Test
  fun `should return all holdings when no platform filter specified`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holding1 = createHolding(1L, "AAPL", "Apple", "Technology")
    val position1 = createPosition(etf1, holding1, BigDecimal("100.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"), Platform.LIGHTYEAR)
    val transaction2 = createCashFlow(etf1, BigDecimal("5"), BigDecimal("100"), Platform.TRADING212)
    setupMocksForBatchLoading(listOf(etf1), listOf(position1), listOf(transaction1, transaction2))

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
    val position1 = createPosition(etf1, holding1, BigDecimal("100.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"), Platform.LIGHTYEAR)
    setupMocksForBatchLoading(listOf(etf1), listOf(position1), listOf(transaction1))

    val result = etfBreakdownService.getHoldingsBreakdown(platforms = listOf("TRADING212"))

    expect(result).toHaveSize(0)
  }

  @Test
  fun `should ignore invalid platform names in filter`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holding1 = createHolding(1L, "AAPL", "Apple", "Technology")
    val position1 = createPosition(etf1, holding1, BigDecimal("100.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"), Platform.LIGHTYEAR)
    setupMocksForBatchLoading(listOf(etf1), listOf(position1), listOf(transaction1))

    val result = etfBreakdownService.getHoldingsBreakdown(platforms = listOf("INVALID_PLATFORM"))

    expect(result).toHaveSize(1)
  }

  @Test
  fun `should combine etf symbols and platform filters with AND logic`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val etf2 = createInstrument(2L, "ETF2", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holding1 = createHolding(1L, "AAPL", "Apple", "Technology")
    val holding2 = createHolding(2L, "MSFT", "Microsoft", "Technology")
    val position1 = createPosition(etf1, holding1, BigDecimal("100.0000"), testDate)
    val position2 = createPosition(etf2, holding2, BigDecimal("100.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"), Platform.LIGHTYEAR)
    val transaction2 = createCashFlow(etf2, BigDecimal("10"), BigDecimal("100"), Platform.TRADING212)
    setupMocksForBatchLoading(listOf(etf1, etf2), listOf(position1, position2), listOf(transaction1, transaction2))

    val result =
      etfBreakdownService.getHoldingsBreakdown(
        etfSymbols = listOf("ETF1"),
        platforms = listOf("LIGHTYEAR"),
      )

    expect(result).toHaveSize(1)
    expect(result[0].holdingTicker).toEqual("AAPL")
    expect(result[0].platforms).toEqual("LIGHTYEAR")
  }

  private fun setupMocksForBatchLoading(
    instruments: List<Instrument>,
    positions: List<EtfPosition>,
    transactions: List<PortfolioTransaction>,
  ) {
    every { instrumentRepository.findAll(any<Specification<Instrument>>()) } returns instruments
    every { etfPositionRepository.findLatestPositionsByEtfIds(any()) } returns positions
    every { transactionRepository.findAllByInstrumentIds(any()) } returns transactions
    every { transactionRepository.findAllByPlatformsAndInstrumentIds(any(), any()) } answers {
      val platforms = firstArg<List<Platform>>()
      transactions.filter { it.platform in platforms }
    }
    every { dailyPriceService.getCurrentPrice(any()) } answers {
      val instrument = firstArg<Instrument>()
      instrument.currentPrice ?: BigDecimal("100")
    }
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

  private fun createCashFlow(
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
      transactionDate = testDate.minusDays(30),
      platform = platform,
    )
}
