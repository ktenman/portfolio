package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.IndustrySector
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.InstrumentCategory
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.model.holding.HoldingPositionRow
import ee.tenman.portfolio.model.holding.InternalHoldingData
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.transaction.InstrumentTransactionData
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.createInstrument
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class SyntheticEtfCalculationServiceTest {
  private val dailyPriceService = mockk<DailyPriceService>()
  private val service = SyntheticEtfCalculationService(dailyPriceService)
  private val uuid = UUID.fromString("0b6f3a52-6d0e-4f43-9a51-2f1c7e8d9a10")
  private val nestle = createInstrument(symbol = "NESN", name = "Nestlé S.A.", currentPrice = BigDecimal("90.00"), id = 7L)

  @BeforeEach
  fun setUp() {
    every { dailyPriceService.getCurrentPrice(any()) } answers { firstArg<Instrument>().currentPrice ?: BigDecimal.ZERO }
  }

  @Test
  fun `should value a holding at its quantity times the current price`() {
    val result = build(nestle, row("NESN"), held(Platform.TREZOR to BigDecimal("10")))
    expect(result.single().value).toEqualNumerically(BigDecimal("900.00"))
  }

  @Test
  fun `should carry the identity sector and country of the holding onto its row`() {
    val result = build(nestle, row("NESN"), held(Platform.TREZOR to BigDecimal("10")))
    expect(labels(result.single())).toEqual(listOf(uuid, "NESN", "Nestlé S.A.", "Consumer Essentials", "CH", "Šveits", "SÜNT:ETF"))
  }

  @Test
  fun `should skip a holding without a ticker`() {
    val result = build(nestle, row(null), held(Platform.TREZOR to BigDecimal("10")))
    expect(result).toBeEmpty()
  }

  @Test
  fun `should skip a holding that matches no instrument`() {
    val result = build(nestle, row("ÕUN"), held(Platform.TREZOR to BigDecimal("10")))
    expect(result).toBeEmpty()
  }

  @Test
  fun `should skip a holding without transactions`() {
    val result = service.buildHoldings(listOf(row("NESN")), "SÜNT:ETF", null, mapOf("NESN" to nestle), emptyMap())
    expect(result).toBeEmpty()
  }

  @Test
  fun `should skip a holding that has been sold out`() {
    val result = build(nestle, row("NESN"), held(Platform.TREZOR to BigDecimal.ZERO))
    expect(result).toBeEmpty()
  }

  @Test
  fun `should skip a holding held only outside the platform filter`() {
    val result = build(nestle, row("NESN"), held(Platform.TREZOR to BigDecimal("10")), setOf(Platform.LHV))
    expect(result).toBeEmpty()
  }

  @Test
  fun `should value only the quantity held on the filtered platforms`() {
    val data = held(Platform.TREZOR to BigDecimal("10"), Platform.LIGHTYEAR to BigDecimal("4"))
    val result = build(nestle, row("NESN"), data, setOf(Platform.LIGHTYEAR))
    expect(result.single().value).toEqualNumerically(BigDecimal("360.00"))
  }

  @Test
  fun `should list only the filtered platforms a holding is held on`() {
    val data = held(Platform.TREZOR to BigDecimal("10"), Platform.LIGHTYEAR to BigDecimal("4"))
    val result = build(nestle, row("NESN"), data, setOf(Platform.LIGHTYEAR))
    expect(result.single().platforms).toEqual(setOf(Platform.LIGHTYEAR))
  }

  @Test
  fun `should label a crypto instrument without a sector as cryptocurrency`() {
    val bitcoin = createInstrument(symbol = "BTCEUR", category = InstrumentCategory.CRYPTO.name, id = 9L)
    val result = build(bitcoin, row("BTCEUR", sector = null), held(Platform.TREZOR to BigDecimal("0.5")))
    expect(result.single().sector).toEqual("Cryptocurrency")
  }

  private fun build(
    instrument: Instrument,
    position: HoldingPositionRow,
    data: InstrumentTransactionData,
    platformFilter: Set<Platform>? = null,
  ): List<InternalHoldingData> =
    service.buildHoldings(
      listOf(position),
      "SÜNT:ETF",
      platformFilter,
      mapOf(instrument.symbol to instrument),
      mapOf(instrument.id to data),
    )

  private fun row(
    ticker: String?,
    sector: IndustrySector? = IndustrySector.CONSUMER_ESSENTIALS,
  ): HoldingPositionRow =
    HoldingPositionRow(
      etfInstrumentId = 3L,
      weightPercentage = BigDecimal("12.5"),
      holdingUuid = uuid,
      ticker = ticker,
      name = "Nestlé S.A.",
      sector = sector,
      industry = null,
      countryCode = "CH",
      countryName = "Šveits",
    )

  private fun held(vararg quantities: Pair<Platform, BigDecimal>): InstrumentTransactionData =
    InstrumentTransactionData(
      netQuantity = quantities.sumOf { it.second },
      platforms = quantities.map { it.first }.toSet(),
      quantityByPlatform = quantities.toMap(),
    )

  private fun labels(holding: InternalHoldingData): List<Any?> =
    listOf(holding.holdingUuid, holding.ticker, holding.name, holding.sector, holding.countryCode, holding.countryName, holding.etfSymbol)
}
