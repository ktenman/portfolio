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
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional

class EtfBreakdownServiceTest {
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val etfPositionRepository = mockk<EtfPositionRepository>()
  private val transactionRepository = mockk<PortfolioTransactionRepository>()
  private val dailyPriceService = mockk<DailyPriceService>()
  private val cacheInvalidationService = mockk<CacheInvalidationService>(relaxed = true)
  private val holdingAggregationService = HoldingAggregationService()
  private val clock = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneId.of("UTC"))
  private val transactionCalculationService = TransactionCalculationService(transactionRepository)
  private val syntheticEtfCalculationService =
    SyntheticEtfCalculationService(
      instrumentRepository,
      etfPositionRepository,
      transactionCalculationService,
      dailyPriceService,
      clock,
    )
  private val testDate = LocalDate.now(clock)
  private lateinit var etfBreakdownService: EtfBreakdownService

  @BeforeEach
  fun setup() {
    etfBreakdownService =
      EtfBreakdownService(
        instrumentRepository,
        etfPositionRepository,
        dailyPriceService,
        cacheInvalidationService,
        holdingAggregationService,
        syntheticEtfCalculationService,
        transactionCalculationService,
        clock,
      )
  }

  @Test
  fun `should deduplicate holdings by name and sector with different tickers`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR)
    val etf2 = createInstrument(2L, "ETF2", ProviderName.LIGHTYEAR)
    val holdingNvidia1 = createHolding(1L, "NVDA", "Nvidia", "Semiconductors")
    val holdingNvidia2 = createHolding(2L, "NVD", "Nvidia", "Semiconductors")
    val position1 =
      createPosition(etf1, holdingNvidia1, BigDecimal("10.0000"), testDate)
    val position2 =
      createPosition(etf2, holdingNvidia2, BigDecimal("5.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"))
    val transaction2 = createCashFlow(etf2, BigDecimal("5"), BigDecimal("200"))
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1, etf2)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { instrumentRepository.findByProviderName(ProviderName.SYNTHETIC) } returns emptyList()
    every { instrumentRepository.findById(1L) } returns Optional.of(etf1)
    every { instrumentRepository.findById(2L) } returns Optional.of(etf2)
    every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position1)
    every { etfPositionRepository.findLatestPositionsByEtfId(2L) } returns listOf(position2)
    every { transactionRepository.findAllByInstrumentId(1L) } returns listOf(transaction1)
    every { transactionRepository.findAllByInstrumentId(2L) } returns listOf(transaction2)

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
    val position1 =
      createPosition(etf1, holdingApple1, BigDecimal("20.0000"), testDate)
    val position2 =
      createPosition(etf2, holdingApple2, BigDecimal("10.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"))
    val transaction2 = createCashFlow(etf2, BigDecimal("5"), BigDecimal("200"))
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1, etf2)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { instrumentRepository.findByProviderName(ProviderName.SYNTHETIC) } returns emptyList()
    every { instrumentRepository.findById(1L) } returns Optional.of(etf1)
    every { instrumentRepository.findById(2L) } returns Optional.of(etf2)
    every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position1)
    every { etfPositionRepository.findLatestPositionsByEtfId(2L) } returns listOf(position2)
    every { transactionRepository.findAllByInstrumentId(1L) } returns listOf(transaction1)
    every { transactionRepository.findAllByInstrumentId(2L) } returns listOf(transaction2)

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
    val position1 =
      createPosition(etf1, holdingNoTicker1, BigDecimal("50.0000"), testDate)
    val position2 =
      createPosition(etf1, holdingNoTicker2, BigDecimal("50.0000"), testDate)
    val transaction = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"))
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { instrumentRepository.findByProviderName(ProviderName.SYNTHETIC) } returns emptyList()
    every { instrumentRepository.findById(1L) } returns Optional.of(etf1)
    every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position1, position2)
    every { transactionRepository.findAllByInstrumentId(1L) } returns listOf(transaction)

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
    val position1 =
      createPosition(etf1, holdingLower, BigDecimal("50.0000"), testDate)
    val position2 =
      createPosition(etf2, holdingUpper, BigDecimal("50.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"))
    val transaction2 = createCashFlow(etf2, BigDecimal("10"), BigDecimal("100"))
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1, etf2)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { instrumentRepository.findByProviderName(ProviderName.SYNTHETIC) } returns emptyList()
    every { instrumentRepository.findById(1L) } returns Optional.of(etf1)
    every { instrumentRepository.findById(2L) } returns Optional.of(etf2)
    every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position1)
    every { etfPositionRepository.findLatestPositionsByEtfId(2L) } returns listOf(position2)
    every { transactionRepository.findAllByInstrumentId(1L) } returns listOf(transaction1)
    every { transactionRepository.findAllByInstrumentId(2L) } returns listOf(transaction2)

    val result = etfBreakdownService.getHoldingsBreakdown()

    expect(result).toHaveSize(1)
    expect(result[0].holdingTicker).toEqual("MSFT")
  }

  @Test
  fun `should return empty list when no etfs found`() {
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns emptyList()
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { instrumentRepository.findByProviderName(ProviderName.SYNTHETIC) } returns emptyList()

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
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1, etf2)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { instrumentRepository.findByProviderName(ProviderName.SYNTHETIC) } returns emptyList()
    every { instrumentRepository.findById(1L) } returns Optional.of(etf1)
    every { instrumentRepository.findById(2L) } returns Optional.of(etf2)
    every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position1)
    every { etfPositionRepository.findLatestPositionsByEtfId(2L) } returns listOf(position2)
    every { transactionRepository.findAllByInstrumentId(1L) } returns listOf(transaction1)
    every { transactionRepository.findAllByInstrumentId(2L) } returns listOf(transaction2)

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
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { instrumentRepository.findByProviderName(ProviderName.SYNTHETIC) } returns listOf(etf2)
    every { instrumentRepository.findById(1L) } returns Optional.of(etf1)
    every { instrumentRepository.findById(2L) } returns Optional.of(etf2)
    every { instrumentRepository.findById(3L) } returns Optional.of(btcInstrument)
    every { instrumentRepository.findBySymbolIn(listOf("BTCEUR")) } returns listOf(btcInstrument)
    every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position1)
    every { etfPositionRepository.findLatestPositionsByEtfId(2L) } returns listOf(position2)
    every { transactionRepository.findAllByInstrumentId(1L) } returns listOf(transaction1)
    every { transactionRepository.findAllByInstrumentId(3L) } returns listOf(btcTransaction)
    every { transactionRepository.findAllByInstrumentIds(listOf(3L)) } returns listOf(btcTransaction)

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
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { instrumentRepository.findByProviderName(ProviderName.SYNTHETIC) } returns emptyList()
    every { instrumentRepository.findById(1L) } returns Optional.of(etf1)
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
    val position1 = createPosition(etf1, holding1, BigDecimal("100.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"), Platform.LIGHTYEAR)
    val transaction2 = createCashFlow(etf1, BigDecimal("5"), BigDecimal("100"), Platform.TRADING212)
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { instrumentRepository.findByProviderName(ProviderName.SYNTHETIC) } returns emptyList()
    every { instrumentRepository.findById(1L) } returns Optional.of(etf1)
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
    val position1 = createPosition(etf1, holding1, BigDecimal("100.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"), Platform.LIGHTYEAR)
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { instrumentRepository.findByProviderName(ProviderName.SYNTHETIC) } returns emptyList()
    every { instrumentRepository.findById(1L) } returns Optional.of(etf1)
    every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position1)
    every { transactionRepository.findAllByInstrumentId(1L) } returns listOf(transaction1)

    val result = etfBreakdownService.getHoldingsBreakdown(platforms = listOf("TRADING212"))

    expect(result).toHaveSize(0)
  }

  @Test
  fun `should ignore invalid platform names in filter`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holding1 = createHolding(1L, "AAPL", "Apple", "Technology")
    val position1 = createPosition(etf1, holding1, BigDecimal("100.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"), Platform.LIGHTYEAR)
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { instrumentRepository.findByProviderName(ProviderName.SYNTHETIC) } returns emptyList()
    every { instrumentRepository.findById(1L) } returns Optional.of(etf1)
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
    val position1 = createPosition(etf1, holding1, BigDecimal("100.0000"), testDate)
    val position2 = createPosition(etf2, holding2, BigDecimal("100.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"), Platform.LIGHTYEAR)
    val transaction2 = createCashFlow(etf2, BigDecimal("10"), BigDecimal("100"), Platform.TRADING212)
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1, etf2)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { instrumentRepository.findByProviderName(ProviderName.SYNTHETIC) } returns emptyList()
    every { instrumentRepository.findById(1L) } returns Optional.of(etf1)
    every { instrumentRepository.findById(2L) } returns Optional.of(etf2)
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

  @Test
  fun `should merge holdings with different company suffixes like Co Ltd and Ltd`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val etf2 = createInstrument(2L, "ETF2", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holdingSamsungCoLtd = createHolding(1L, "005930", "Samsung Electronics Co Ltd", "Technology")
    val holdingSamsungLtd = createHolding(2L, null, "Samsung Electronics Ltd", "Technology")
    val position1 = createPosition(etf1, holdingSamsungCoLtd, BigDecimal("50.0000"), testDate)
    val position2 = createPosition(etf2, holdingSamsungLtd, BigDecimal("50.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"))
    val transaction2 = createCashFlow(etf2, BigDecimal("10"), BigDecimal("100"))
    setupTwoEtfMergeScenario(etf1, etf2, position1, position2, transaction1, transaction2)

    val result = etfBreakdownService.getHoldingsBreakdown()

    expect(result).toHaveSize(1)
    expect(result[0].holdingName).toEqual("Samsung Electronics")
    expect(result[0].holdingTicker).toEqual("005930")
  }

  @Test
  fun `should merge holdings with ADR suffix`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val etf2 = createInstrument(2L, "ETF2", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holdingNiceAdr = createHolding(1L, "NICE", "Nice Ltd - Spon ADR", "Technology")
    val holdingNice = createHolding(2L, null, "Nice", "Technology")
    val position1 = createPosition(etf1, holdingNiceAdr, BigDecimal("50.0000"), testDate)
    val position2 = createPosition(etf2, holdingNice, BigDecimal("50.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"))
    val transaction2 = createCashFlow(etf2, BigDecimal("10"), BigDecimal("100"))
    setupTwoEtfMergeScenario(etf1, etf2, position1, position2, transaction1, transaction2)

    val result = etfBreakdownService.getHoldingsBreakdown()

    expect(result).toHaveSize(1)
    expect(result[0].holdingName).toEqual("Nice")
    expect(result[0].holdingTicker).toEqual("NICE")
  }

  @Test
  fun `should merge Meta Platforms Inc with Meta`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val etf2 = createInstrument(2L, "ETF2", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holdingMetaPlatforms = createHolding(1L, "FB", "Meta Platforms, Inc. Cl A", "Technology")
    val holdingMeta = createHolding(2L, "META", "Meta", "Technology")
    val position1 = createPosition(etf1, holdingMetaPlatforms, BigDecimal("50.0000"), testDate)
    val position2 = createPosition(etf2, holdingMeta, BigDecimal("50.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"))
    val transaction2 = createCashFlow(etf2, BigDecimal("10"), BigDecimal("100"))
    setupTwoEtfMergeScenario(etf1, etf2, position1, position2, transaction1, transaction2)

    val result = etfBreakdownService.getHoldingsBreakdown()

    expect(result).toHaveSize(1)
    expect(result[0].holdingName).toEqual("Meta")
    expect(result[0].holdingTicker).toEqual("META")
  }

  @Test
  fun `should merge Nice Systems with Nice`() {
    val etf1 = createInstrument(1L, "ETF1", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val etf2 = createInstrument(2L, "ETF2", ProviderName.LIGHTYEAR, BigDecimal("100"))
    val holdingNiceSystems = createHolding(1L, "NICE", "Nice Systems", "Technology")
    val holdingNice = createHolding(2L, "NICE", "Nice", "Technology")
    val position1 = createPosition(etf1, holdingNiceSystems, BigDecimal("50.0000"), testDate)
    val position2 = createPosition(etf2, holdingNice, BigDecimal("50.0000"), testDate)
    val transaction1 = createCashFlow(etf1, BigDecimal("10"), BigDecimal("100"))
    val transaction2 = createCashFlow(etf2, BigDecimal("10"), BigDecimal("100"))
    setupTwoEtfMergeScenario(etf1, etf2, position1, position2, transaction1, transaction2)

    val result = etfBreakdownService.getHoldingsBreakdown()

    expect(result).toHaveSize(1)
    expect(result[0].holdingName).toEqual("Nice")
    expect(result[0].holdingTicker).toEqual("NICE")
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

  private fun setupTwoEtfMergeScenario(
    etf1: Instrument,
    etf2: Instrument,
    position1: EtfPosition,
    position2: EtfPosition,
    transaction1: PortfolioTransaction,
    transaction2: PortfolioTransaction,
  ) {
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(etf1, etf2)
    every { instrumentRepository.findByProviderName(ProviderName.FT) } returns emptyList()
    every { instrumentRepository.findByProviderName(ProviderName.SYNTHETIC) } returns emptyList()
    every { instrumentRepository.findById(1L) } returns Optional.of(etf1)
    every { instrumentRepository.findById(2L) } returns Optional.of(etf2)
    every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position1)
    every { etfPositionRepository.findLatestPositionsByEtfId(2L) } returns listOf(position2)
    every { transactionRepository.findAllByInstrumentId(1L) } returns listOf(transaction1)
    every { transactionRepository.findAllByInstrumentId(2L) } returns listOf(transaction2)
  }
}
