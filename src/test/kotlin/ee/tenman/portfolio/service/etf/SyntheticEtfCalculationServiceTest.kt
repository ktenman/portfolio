package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.InstrumentCategory
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.transaction.InstrumentTransactionData
import ee.tenman.portfolio.service.transaction.TransactionCalculationService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class SyntheticEtfCalculationServiceTest {
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val etfPositionRepository = mockk<EtfPositionRepository>()
  private val transactionCalculationService = mockk<TransactionCalculationService>()
  private val dailyPriceService = mockk<DailyPriceService>()
  private lateinit var service: SyntheticEtfCalculationService

  @BeforeEach
  fun setup() {
    every { dailyPriceService.getCurrentPrice(any()) } answers {
      val instrument = firstArg<Instrument>()
      instrument.currentPrice ?: BigDecimal.ZERO
    }
    service =
      SyntheticEtfCalculationService(
        instrumentRepository,
        etfPositionRepository,
        transactionCalculationService,
        dailyPriceService,
      )
  }

  @Nested
  inner class HasActiveHoldings {
    @Test
    fun `should return false when no positions exist`() {
      every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns emptyList()

      val result = service.hasActiveHoldings(1L)

      expect(result).toEqual(false)
    }

    @Test
    fun `should return false when positions have no tickers`() {
      val holding = createHolding(null, "Apple Inc")
      val position = createPosition(holding)
      every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position)
      every { instrumentRepository.findBySymbolIn(emptyList()) } returns emptyList()

      val result = service.hasActiveHoldings(1L)

      expect(result).toEqual(false)
    }

    @Test
    fun `should return true when holding has active position with quantity`() {
      val holding = createHolding("AAPL", "Apple Inc")
      val position = createPosition(holding)
      val instrument = createInstrument("AAPL", BigDecimal("150.00"))
      every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position)
      every { instrumentRepository.findBySymbolIn(listOf("AAPL")) } returns listOf(instrument)
      every { transactionCalculationService.batchCalculateAll(listOf(instrument.id), null) } returns
        mapOf(
          instrument.id to
            InstrumentTransactionData(
            BigDecimal("10"),
            setOf(Platform.BINANCE),
            mapOf(Platform.BINANCE to BigDecimal("10")),
          ),
            )

      val result = service.hasActiveHoldings(1L)

      expect(result).toEqual(true)
    }

    @Test
    fun `should return false when all positions are sold out`() {
      val holding = createHolding("AAPL", "Apple Inc")
      val position = createPosition(holding)
      val instrument = createInstrument("AAPL", BigDecimal("150.00"))
      every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position)
      every { instrumentRepository.findBySymbolIn(listOf("AAPL")) } returns listOf(instrument)
      every { transactionCalculationService.batchCalculateAll(listOf(instrument.id), null) } returns
        mapOf(instrument.id to InstrumentTransactionData(BigDecimal.ZERO, emptySet(), emptyMap()))

      val result = service.hasActiveHoldings(1L)

      expect(result).toEqual(false)
    }

    @Test
    fun `should return false when no instruments found`() {
      val holding = createHolding("AAPL", "Apple Inc")
      val position = createPosition(holding)
      every { etfPositionRepository.findLatestPositionsByEtfId(1L) } returns listOf(position)
      every { instrumentRepository.findBySymbolIn(listOf("AAPL")) } returns emptyList()

      val result = service.hasActiveHoldings(1L)

      expect(result).toEqual(false)
    }
  }

  @Nested
  inner class CalculateHoldingValues {
    @Test
    fun `should return empty list when no positions`() {
      val result = service.calculateHoldingValues(emptyList())

      expect(result).toHaveSize(0)
    }

    @Test
    fun `should calculate holding value correctly`() {
      val holding = createHolding("AAPL", "Apple Inc")
      val position = createPosition(holding)
      val instrument = createInstrument("AAPL", BigDecimal("150.00"))
      every { instrumentRepository.findBySymbolIn(listOf("AAPL")) } returns listOf(instrument)
      every { transactionCalculationService.batchCalculateAll(listOf(instrument.id)) } returns
        mapOf(
          instrument.id to
            InstrumentTransactionData(
              netQuantity = BigDecimal("10"),
              platforms = setOf(Platform.TRADING212),
            ),
        )

      val result = service.calculateHoldingValues(listOf(position))

      expect(result).toHaveSize(1)
      expect(result[0].value).toEqualNumerically(BigDecimal("1500.00"))
    }

    @Test
    fun `should skip positions without ticker`() {
      val holding = createHolding(null, "Unknown Holding")
      val position = createPosition(holding)
      every { instrumentRepository.findBySymbolIn(emptyList()) } returns emptyList()

      val result = service.calculateHoldingValues(listOf(position))

      expect(result).toHaveSize(0)
    }

    @Test
    fun `should skip positions without matching instrument`() {
      val holding = createHolding("UNKNOWN", "Unknown Company")
      val position = createPosition(holding)
      every { instrumentRepository.findBySymbolIn(listOf("UNKNOWN")) } returns emptyList()
      every { transactionCalculationService.batchCalculateAll(emptyList()) } returns emptyMap()

      val result = service.calculateHoldingValues(listOf(position))

      expect(result).toHaveSize(0)
    }

    @Test
    fun `should skip positions without transaction data`() {
      val holding = createHolding("AAPL", "Apple Inc")
      val position = createPosition(holding)
      val instrument = createInstrument("AAPL", BigDecimal("150.00"))
      every { instrumentRepository.findBySymbolIn(listOf("AAPL")) } returns listOf(instrument)
      every { transactionCalculationService.batchCalculateAll(listOf(instrument.id)) } returns emptyMap()

      val result = service.calculateHoldingValues(listOf(position))

      expect(result).toHaveSize(0)
    }
  }

  @Nested
  inner class BuildHoldings {
    @Test
    fun `should build internal holding data from positions`() {
      val holding = createHolding("AAPL", "Apple Inc", "Technology", "US", "United States")
      val position = createPosition(holding)
      val instrument = createInstrument("AAPL", BigDecimal("150.00"))
      every { instrumentRepository.findBySymbolIn(listOf("AAPL")) } returns listOf(instrument)
      every { transactionCalculationService.batchCalculateAll(listOf(instrument.id)) } returns
        mapOf(
          instrument.id to
            InstrumentTransactionData(
              netQuantity = BigDecimal("10"),
              platforms = setOf(Platform.TRADING212),
            ),
        )

      val result = service.buildHoldings(listOf(position), "CRYPTO-ETF")

      expect(result).toHaveSize(1)
      expect(result[0].ticker).toEqual("AAPL")
      expect(result[0].name).toEqual("Apple Inc")
      expect(result[0].sector).toEqual("Technology")
      expect(result[0].countryCode).toEqual("US")
      expect(result[0].countryName).toEqual("United States")
      expect(result[0].etfSymbol).toEqual("CRYPTO-ETF")
    }
  }

  @Nested
  inner class CalculateTotalValue {
    @Test
    fun `should calculate total value across multiple ETFs`() {
      val holding1 = createHolding("AAPL", "Apple")
      val holding2 = createHolding("MSFT", "Microsoft")
      val position1 = createPosition(holding1)
      val position2 = createPosition(holding2)
      val instrument1 = createInstrument("AAPL", BigDecimal("100.00"))
      val instrument2 = createInstrument("MSFT", BigDecimal("200.00"))
      val etf = createEtfInstrument("SYNTH-ETF")
      every { etfPositionRepository.findLatestPositionsByEtfIds(listOf(etf.id)) } returns listOf(position1, position2)
      every { instrumentRepository.findBySymbolIn(listOf("AAPL", "MSFT")) } returns listOf(instrument1, instrument2)
      every { transactionCalculationService.batchCalculateAll(listOf(instrument1.id, instrument2.id)) } returns
        mapOf(
          instrument1.id to
            InstrumentTransactionData(
              netQuantity = BigDecimal("5"),
              platforms = setOf(Platform.TRADING212),
            ),
          instrument2.id to
            InstrumentTransactionData(
              netQuantity = BigDecimal("3"),
              platforms = setOf(Platform.TRADING212),
            ),
        )

      val result = service.calculateTotalValue(listOf(etf))

      expect(result).toEqualNumerically(BigDecimal("1100.00"))
    }

    @Test
    fun `should return zero for empty ETF list`() {
      val result = service.calculateTotalValue(emptyList())

      expect(result).toEqualNumerically(BigDecimal.ZERO)
    }
  }

  private fun createHolding(
    ticker: String?,
    name: String,
    sector: String? = null,
    countryCode: String? = null,
    countryName: String? = null,
  ): EtfHolding =
    EtfHolding(ticker = ticker, name = name).apply {
      this.id = System.nanoTime()
      this.sector = sector
      this.countryCode = countryCode
      this.countryName = countryName
    }

  private fun createPosition(holding: EtfHolding): EtfPosition =
    EtfPosition(
      etfInstrument = createEtfInstrument("SYNTH-ETF"),
      holding = holding,
      snapshotDate = LocalDate.of(2024, 1, 15),
      weightPercentage = BigDecimal("5.00"),
    )

  private fun createInstrument(
    symbol: String,
    price: BigDecimal,
  ): Instrument =
    Instrument(
      symbol = symbol,
      name = "$symbol Company",
      category = "Stock",
      baseCurrency = "EUR",
      providerName = ProviderName.TRADING212,
    ).apply {
      this.id = symbol.hashCode().toLong().let { if (it < 0) -it else it }
      this.currentPrice = price
    }

  private fun createEtfInstrument(symbol: String): Instrument =
    Instrument(
      symbol = symbol,
      name = "$symbol ETF",
      category = InstrumentCategory.ETF.name,
      baseCurrency = "EUR",
      providerName = ProviderName.SYNTHETIC,
    ).apply {
      this.id = symbol.hashCode().toLong().let { if (it < 0) -it else it }
    }
}
