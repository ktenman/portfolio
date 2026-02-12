package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.transaction.TransactionCalculationService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.domain.Specification
import java.math.BigDecimal
import java.time.LocalDate

class EtfAvailabilityServiceTest {
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val transactionRepository = mockk<PortfolioTransactionRepository>()
  private val etfPositionRepository = mockk<EtfPositionRepository>()
  private val dailyPriceService = mockk<DailyPriceService>()
  private val transactionCalculationService = TransactionCalculationService(transactionRepository)
  private val syntheticEtfCalculationService =
    SyntheticEtfCalculationService(instrumentRepository, etfPositionRepository, transactionCalculationService, dailyPriceService)
  private lateinit var service: EtfAvailabilityService

  @BeforeEach
  fun setup() {
    service = EtfAvailabilityService(instrumentRepository, transactionCalculationService, syntheticEtfCalculationService)
  }

  @Test
  fun `should return all ETFs when no platform filter`() {
    val etf1 = createInstrument(1L, "QDVE:GER:EUR", ProviderName.LIGHTYEAR)
    val etf2 = createInstrument(2L, "VUAA:GER:EUR", ProviderName.FT)
    val tx1 = createTransaction(etf1, BigDecimal("10"), Platform.LIGHTYEAR)
    val tx2 = createTransaction(etf2, BigDecimal("5"), Platform.LHV)
    every { instrumentRepository.findAll(any<Specification<Instrument>>()) } returns listOf(etf1, etf2)
    every { transactionRepository.findAllByInstrumentIds(any()) } returns listOf(tx1, tx2)

    val result = service.getAvailableEtfs(null)

    expect(result.etfSymbols).toHaveSize(2)
    expect(result.etfSymbols).toContainExactly("QDVE:GER:EUR", "VUAA:GER:EUR")
  }

  @Test
  fun `should return only ETFs active on specified platforms`() {
    val etf1 = createInstrument(1L, "QDVE:GER:EUR", ProviderName.LIGHTYEAR)
    val tx1 = createTransaction(etf1, BigDecimal("10"), Platform.LIGHTYEAR)
    every { instrumentRepository.findAll(any<Specification<Instrument>>()) } returns listOf(etf1)
    every { transactionRepository.findAllByPlatformsAndInstrumentIds(any(), any()) } returns listOf(tx1)

    val result = service.getAvailableEtfs(listOf("LIGHTYEAR"))

    expect(result.etfSymbols).toContainExactly("QDVE:GER:EUR")
    expect(result.platforms).toContainExactly("LIGHTYEAR")
  }

  @Test
  fun `should exclude ETFs with zero net quantity`() {
    val etf1 = createInstrument(1L, "QDVE:GER:EUR", ProviderName.LIGHTYEAR)
    val buy = createTransaction(etf1, BigDecimal("10"), Platform.LIGHTYEAR)
    val sell = createTransaction(etf1, BigDecimal("10"), Platform.LIGHTYEAR, TransactionType.SELL)
    every { instrumentRepository.findAll(any<Specification<Instrument>>()) } returns listOf(etf1)
    every { transactionRepository.findAllByInstrumentIds(any()) } returns listOf(buy, sell)

    val result = service.getAvailableEtfs(null)

    expect(result.etfSymbols).toHaveSize(0)
  }

  @Test
  fun `should include synthetic ETFs with active holdings`() {
    val synthetic = createInstrument(1L, "TREZOR", ProviderName.SYNTHETIC)
    val btcInstrument = createInstrument(2L, "BTCEUR", ProviderName.BINANCE)
    val btcHolding =
      ee.tenman.portfolio.domain
      .EtfHolding(ticker = "BTCEUR", name = "Bitcoin")
      .apply { id = 1L }
    val position =
      ee.tenman.portfolio.domain.EtfPosition(
      etfInstrument = synthetic,
      holding = btcHolding,
      weightPercentage = BigDecimal("100"),
      snapshotDate = LocalDate.of(2024, 1, 15),
    )
    val btcTx = createTransaction(btcInstrument, BigDecimal("1"), Platform.BINANCE)
    every { instrumentRepository.findAll(any<Specification<Instrument>>()) } returns listOf(synthetic)
    every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position)
    every { instrumentRepository.findBySymbolIn(listOf("BTCEUR")) } returns listOf(btcInstrument)
    every { transactionRepository.findAllByInstrumentIds(listOf(2L)) } returns listOf(btcTx)

    val result = service.getAvailableEtfs(null)

    expect(result.etfSymbols).toContainExactly("TREZOR")
  }

  @Test
  fun `should ignore invalid platform names`() {
    val etf1 = createInstrument(1L, "QDVE:GER:EUR", ProviderName.LIGHTYEAR)
    val tx1 = createTransaction(etf1, BigDecimal("10"), Platform.LIGHTYEAR)
    every { instrumentRepository.findAll(any<Specification<Instrument>>()) } returns listOf(etf1)
    every { transactionRepository.findAllByInstrumentIds(any()) } returns listOf(tx1)

    val result = service.getAvailableEtfs(listOf("INVALID_PLATFORM"))

    expect(result.etfSymbols).toHaveSize(1)
    expect(result.etfSymbols).toContainExactly("QDVE:GER:EUR")
  }

  @Test
  fun `should return empty when no instruments found`() {
    every { instrumentRepository.findAll(any<Specification<Instrument>>()) } returns emptyList()

    val result = service.getAvailableEtfs(null)

    expect(result.etfSymbols).toHaveSize(0)
    expect(result.platforms).toHaveSize(0)
  }

  @Test
  fun `should return sorted symbols and platforms`() {
    val etf1 = createInstrument(1L, "VUAA:GER:EUR", ProviderName.LIGHTYEAR)
    val etf2 = createInstrument(2L, "QDVE:GER:EUR", ProviderName.FT)
    val tx1 = createTransaction(etf1, BigDecimal("10"), Platform.TRADING212)
    val tx2 = createTransaction(etf2, BigDecimal("5"), Platform.LIGHTYEAR)
    every { instrumentRepository.findAll(any<Specification<Instrument>>()) } returns listOf(etf1, etf2)
    every { transactionRepository.findAllByInstrumentIds(any()) } returns listOf(tx1, tx2)

    val result = service.getAvailableEtfs(null)

    expect(result.etfSymbols).toEqual(listOf("QDVE:GER:EUR", "VUAA:GER:EUR"))
    expect(result.platforms).toEqual(listOf("LIGHTYEAR", "TRADING212"))
  }

  private fun createInstrument(
    id: Long,
    symbol: String,
    providerName: ProviderName,
  ): Instrument =
    Instrument(
      symbol = symbol,
      name = "Test $symbol",
      category = "ETF",
      baseCurrency = "EUR",
      currentPrice = BigDecimal("100"),
      providerName = providerName,
    ).apply { this.id = id }

  private fun createTransaction(
    instrument: Instrument,
    quantity: BigDecimal,
    platform: Platform,
    type: TransactionType = TransactionType.BUY,
  ): PortfolioTransaction =
    PortfolioTransaction(
      instrument = instrument,
      transactionType = type,
      quantity = quantity,
      price = BigDecimal("100"),
      transactionDate = LocalDate.of(2024, 1, 15),
      platform = platform,
    )
}
