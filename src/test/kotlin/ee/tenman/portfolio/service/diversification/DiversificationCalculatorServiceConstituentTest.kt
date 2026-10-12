package ee.tenman.portfolio.service.diversification

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.IndustrySector
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.dto.EtfDetailDto
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.etf.SyntheticEtfCalculationService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class DiversificationCalculatorServiceConstituentTest {
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val etfPositionRepository = mockk<EtfPositionRepository>()
  private lateinit var service: DiversificationCalculatorService

  @BeforeEach
  fun setup() {
    service = DiversificationCalculatorService(instrumentRepository, etfPositionRepository, mockk<SyntheticEtfCalculationService>())
  }

  @Test
  fun `should list the constituents of a synthetic fund in place of the fund`() {
    val trezor = fund(20L, "TREZOR", "ETF")
    val etf = instrument(1L, "VWCE", "ETF", ProviderName.LIGHTYEAR)
    listed(listOf(trezor), trezorPositions(trezor), listOf(bitcoin(), binance()), listOf(etf))

    val symbols = service.getAvailableEtfs().map { it.symbol }

    expect(symbols).toEqual(listOf("BNBEUR", "BTCEUR", "VWCE"))
  }

  @Test
  fun `should name a constituent after its holding and keep its own figures`() {
    val trezor = fund(20L, "TREZOR", "CRYPTO")
    val position = position(trezor, holding("BNBEUR", "Binance Coin", IndustrySector.CRYPTOCURRENCY))
    listed(listOf(trezor), listOf(position), listOf(binance()))

    val row = service.getAvailableEtfs().single()

    expect(row).toEqual(
      EtfDetailDto(13L, "BNBEUR", "Binance Coin", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal("0.3712"), BigDecimal("512.40"), null),
    )
  }

  @Test
  fun `should list a cash constituent that is neither an etf nor crypto`() {
    val cash = fund(23L, "CASH", "CASH")
    val euro = instrument(14L, "EUR", "CASH", ProviderName.MANUAL, BigDecimal.ONE)
    listed(listOf(cash), listOf(position(cash, holding("EUR", "Euro Cash", IndustrySector.FINANCE))), listOf(euro))

    val rows = service.getAvailableEtfs().map { it.name to it.currentPrice }

    expect(rows).toEqual(listOf("Euro Cash" to BigDecimal.ONE))
  }

  @Test
  fun `should leave out a holding whose ticker has no instrument`() {
    val trezor = fund(20L, "TREZOR", "CRYPTO")
    listed(listOf(trezor), trezorPositions(trezor), listOf(bitcoin()))

    val symbols = service.getAvailableEtfs().map { it.symbol }

    expect(symbols).toEqual(listOf("BTCEUR"))
  }

  private fun listed(
    funds: List<Instrument>,
    positions: List<EtfPosition>,
    coins: List<Instrument>,
    etfs: List<Instrument> = emptyList(),
  ) {
    val ids = (etfs + funds).map { it.id }
    val tickers = positions.mapNotNull { it.holding.ticker }.toSet()
    every { etfPositionRepository.findDistinctEtfInstrumentIds() } returns ids
    every { instrumentRepository.findAllById(ids) } returns etfs + funds
    every { instrumentRepository.findByProviderName(ProviderName.SYNTHETIC) } returns funds
    every { etfPositionRepository.findLatestPositionsByEtfIds(funds.map { it.id }) } returns positions
    every { instrumentRepository.findBySymbolIn(match { it.toSet() == tickers }) } returns coins
  }

  private fun trezorPositions(fund: Instrument): List<EtfPosition> =
    listOf(
      position(fund, holding("BTCEUR", "Bitcoin", IndustrySector.CRYPTOCURRENCY)),
      position(fund, holding("BNBEUR", "Binance Coin", IndustrySector.CRYPTOCURRENCY)),
    )

  private fun bitcoin(): Instrument = instrument(2L, "BTCEUR", "CRYPTO", ProviderName.BINANCE, BigDecimal("61250.75"), BigDecimal("0.2541"))

  private fun binance(): Instrument =
    instrument(13L, "BNBEUR", "Cryptocurrency", ProviderName.BINANCE, BigDecimal("512.40"), BigDecimal("0.3712"))

  private fun fund(
    id: Long,
    symbol: String,
    category: String,
  ): Instrument = instrument(id, symbol, category, ProviderName.SYNTHETIC, null, null, null)

  private fun instrument(
    id: Long,
    symbol: String,
    category: String,
    provider: ProviderName,
    price: BigDecimal? = BigDecimal("100.00"),
    annualReturn: BigDecimal? = BigDecimal.ZERO,
    ter: BigDecimal? = BigDecimal.ZERO,
  ): Instrument =
    Instrument(
      symbol = symbol,
      name = "Õige $symbol",
      category = category,
      baseCurrency = "EUR",
      currentPrice = price,
      providerName = provider,
    ).apply {
      this.id = id
      this.ter = ter
      this.xirrAnnualReturn = annualReturn
    }

  private fun holding(
    ticker: String,
    name: String,
    sector: IndustrySector,
  ): EtfHolding = EtfHolding(ticker = ticker, name = name, sector = sector)

  private fun position(
    fund: Instrument,
    holding: EtfHolding,
  ): EtfPosition = EtfPosition(etfInstrument = fund, holding = holding, weightPercentage = BigDecimal.ZERO, snapshotDate = SNAPSHOT_DATE)

  companion object {
    private val SNAPSHOT_DATE = LocalDate.of(2026, 8, 28)
  }
}
