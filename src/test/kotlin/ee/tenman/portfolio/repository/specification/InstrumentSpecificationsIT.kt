package ee.tenman.portfolio.repository.specification

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@IntegrationTest
class InstrumentSpecificationsIT {
  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var transactionRepository: PortfolioTransactionRepository

  private val testDate = LocalDate.of(2024, 1, 15)

  @BeforeEach
  fun setup() {
    transactionRepository.deleteAll()
    instrumentRepository.deleteAll()
  }

  @Test
  fun `should find instruments by provider name`() {
    val lightyear = saveInstrument("QDVE", ProviderName.LIGHTYEAR)
    saveInstrument("BTCEUR", ProviderName.BINANCE)
    val spec = InstrumentSpecifications.hasProviderNameIn(listOf(ProviderName.LIGHTYEAR))

    val result = instrumentRepository.findAll(spec)

    expect(result).toHaveSize(1)
    expect(result[0].id).toEqual(lightyear.id)
  }

  @Test
  fun `should find instruments by symbol`() {
    val qdve = saveInstrument("QDVE", ProviderName.LIGHTYEAR)
    saveInstrument("VUAA", ProviderName.LIGHTYEAR)
    val spec = InstrumentSpecifications.hasSymbolIn(listOf("QDVE"))

    val result = instrumentRepository.findAll(spec)

    expect(result).toHaveSize(1)
    expect(result[0].id).toEqual(qdve.id)
  }

  @Test
  fun `should find instruments with transactions on specific platforms`() {
    val etf1 = saveInstrument("QDVE", ProviderName.LIGHTYEAR)
    val etf2 = saveInstrument("VUAA", ProviderName.FT)
    saveTransaction(etf1, Platform.LIGHTYEAR)
    saveTransaction(etf2, Platform.LHV)
    val spec = InstrumentSpecifications.hasTransactionsOnPlatforms(setOf(Platform.LIGHTYEAR))

    val result = instrumentRepository.findAll(spec)

    expect(result).toHaveSize(1)
    expect(result[0].symbol).toEqual("QDVE")
  }

  @Test
  fun `should find instruments on multiple platforms`() {
    val etf1 = saveInstrument("QDVE", ProviderName.LIGHTYEAR)
    val etf2 = saveInstrument("VUAA", ProviderName.FT)
    val etf3 = saveInstrument("IWDA", ProviderName.LIGHTYEAR)
    saveTransaction(etf1, Platform.LIGHTYEAR)
    saveTransaction(etf2, Platform.LHV)
    saveTransaction(etf3, Platform.SWEDBANK)
    val spec = InstrumentSpecifications.hasTransactionsOnPlatforms(setOf(Platform.LHV, Platform.SWEDBANK))

    val result = instrumentRepository.findAll(spec)

    expect(result.map { it.symbol }.sorted()).toContainExactly("IWDA", "VUAA")
  }

  @Test
  fun `should compose provider name and platform specifications`() {
    val etf1 = saveInstrument("QDVE", ProviderName.LIGHTYEAR)
    val etf2 = saveInstrument("BTCEUR", ProviderName.BINANCE)
    saveTransaction(etf1, Platform.LIGHTYEAR)
    saveTransaction(etf2, Platform.LIGHTYEAR)
    val spec =
      InstrumentSpecifications
        .hasProviderNameIn(listOf(ProviderName.LIGHTYEAR))
      .and(InstrumentSpecifications.hasTransactionsOnPlatforms(setOf(Platform.LIGHTYEAR)))

    val result = instrumentRepository.findAll(spec)

    expect(result).toHaveSize(1)
    expect(result[0].symbol).toEqual("QDVE")
  }

  @Test
  fun `should compose provider name and symbol specifications`() {
    saveInstrument("QDVE", ProviderName.LIGHTYEAR)
    val vuaa = saveInstrument("VUAA", ProviderName.LIGHTYEAR)
    saveInstrument("BTCEUR", ProviderName.BINANCE)
    val spec =
      InstrumentSpecifications
        .hasProviderNameIn(listOf(ProviderName.LIGHTYEAR))
      .and(InstrumentSpecifications.hasSymbolIn(listOf("VUAA")))

    val result = instrumentRepository.findAll(spec)

    expect(result).toHaveSize(1)
    expect(result[0].id).toEqual(vuaa.id)
  }

  @Test
  fun `should return empty when no instruments match platform`() {
    saveInstrument("QDVE", ProviderName.LIGHTYEAR)
    val spec = InstrumentSpecifications.hasTransactionsOnPlatforms(setOf(Platform.LIGHTYEAR))

    val result = instrumentRepository.findAll(spec)

    expect(result).toHaveSize(0)
  }

  private fun saveInstrument(
    symbol: String,
    providerName: ProviderName,
  ): Instrument =
    instrumentRepository.save(
      Instrument(
        symbol = symbol,
        name = "Test $symbol",
        category = "ETF",
        baseCurrency = "EUR",
        providerName = providerName,
      ),
    )

  private fun saveTransaction(
    instrument: Instrument,
    platform: Platform,
  ): PortfolioTransaction =
    transactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        transactionDate = testDate,
        platform = platform,
      ),
    )
}
