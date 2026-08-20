package ee.tenman.portfolio.lightyear

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Currency
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

class LightyearPriceServiceTest : LightyearPriceServiceTestBase() {
  private fun createPriceResponse(
    price: BigDecimal,
    currency: String = "EUR",
  ) = LightyearPriceResponse(
    timestamp = "2025-01-01T00:00:00Z",
    price = price,
    change = BigDecimal.ZERO,
    changePercent = BigDecimal.ZERO,
    currency = currency,
  )

  private fun createInstrument(
    id: String,
    symbol: String,
    name: String,
    logo: String? = null,
    sector: String? = null,
  ) = LightyearInstrumentResponse(
    id = id,
    symbol = symbol,
    name = name,
    exchange = "NASDAQ",
    logo = logo,
    summary = sector?.let { LightyearInstrumentSummary(sector = it) },
  )

  private fun stubHoldings(vararg holdings: LightyearHoldingResponse) {
    every { properties.findUuidBySymbol(any()) } returns "etf-uuid"
    every { lightyearPriceClient.getHoldings(any()) } returns holdings.toList()
  }

  private fun stubBatch(vararg instruments: LightyearInstrumentResponse) {
    every { lightyearPriceClient.getInstrumentBatch(instruments.map { it.id }) } returns instruments.toList()
  }

  private fun stubSymbols(vararg symbols: Pair<String, String>) {
    every { properties.getAllSymbols() } returns symbols.map { it.first }
    symbols.forEach { (symbol, uuid) -> every { properties.findUuidBySymbol(symbol) } returns uuid }
  }

  @Test
  fun `should return empty list when symbol has no UUID mapping`() {
    every { properties.findUuidBySymbol("UNKNOWN:SYMBOL") } returns null
    every { instrumentRepository.findBySymbol("UNKNOWN:SYMBOL") } returns Optional.empty()
    every { lightyearPriceClient.lookupUuid("UNKNOWN:SYMBOL") } throws RuntimeException("Not found")

    val result = service.fetchHoldingsAsDto("UNKNOWN:SYMBOL")

    expect(result).toBeEmpty()
    verify(exactly = 0) { lightyearPriceClient.getHoldings(any()) }
  }

  @Test
  fun `should fetch holdings and map to DTOs with instrument details`() {
    stubHoldings(
      LightyearHoldingResponse(name = "Apple Inc", value = 5.5, instrumentId = "inst-1"),
      LightyearHoldingResponse(name = "Microsoft Corp", value = 4.2, instrumentId = "inst-2"),
    )
    stubBatch(
      createInstrument("inst-1", "AAPL", "Apple Inc", "https://logo.com/aapl.png", "Technology"),
      createInstrument("inst-2", "MSFT", "Microsoft Corp", "https://logo.com/msft.png", "Software"),
    )

    val result = service.fetchHoldingsAsDto("VUAA")

    expect(result).toHaveSize(2)
    expect(result[0].name).toEqual("Apple Inc")
    expect(result[0].ticker).toEqual("AAPL")
    expect(result[0].sector).toEqual("Technology")
    expect(result[0].weight).toEqualNumerically(BigDecimal("56.701031"))
    expect(result[0].rank).toEqual(1)
    expect(result[0].logoUrl).toEqual("https://logo.com/aapl.png")
    expect(result[1].name).toEqual("Microsoft Corp")
    expect(result[1].ticker).toEqual("MSFT")
    expect(result[1].sector).toEqual("Software")
    expect(result[1].weight).toEqualNumerically(BigDecimal("43.298969"))
    expect(result[1].rank).toEqual(2)
  }

  @Test
  fun `should handle holdings without instrument IDs`() {
    stubHoldings(LightyearHoldingResponse(name = "Unknown Company", value = 1.5, instrumentId = null))

    val result = service.fetchHoldingsAsDto("VUAA")

    expect(result).toHaveSize(1)
    expect(result[0].name).toEqual("Unknown Company")
    expect(result[0].ticker).toEqual(null)
    expect(result[0].sector).toEqual(null)
    expect(result[0].weight).toEqualNumerically(BigDecimal("100.000000"))
    expect(result[0].rank).toEqual(1)
    verify(exactly = 0) { lightyearPriceClient.getInstrumentBatch(any()) }
  }

  @Test
  fun `should handle empty holdings list`() {
    stubHoldings()

    val result = service.fetchHoldingsAsDto("VUAA")

    expect(result).toBeEmpty()
    verify(exactly = 0) { lightyearPriceClient.getInstrumentBatch(any()) }
  }

  @Test
  fun `should handle batch fetch failure gracefully after retries`() {
    stubHoldings(LightyearHoldingResponse(name = "Apple Inc", value = 5.5, instrumentId = "inst-1"))
    every { lightyearPriceClient.getInstrumentBatch(any()) } throws RuntimeException("API error")

    val result = service.fetchHoldingsAsDto("VUAA")

    expect(result).toHaveSize(1)
    expect(result[0].name).toEqual("Apple Inc")
    expect(result[0].ticker).toEqual(null)
    expect(result[0].sector).toEqual(null)
    verify(exactly = 3) { lightyearPriceClient.getInstrumentBatch(any()) }
  }

  @Test
  fun `should succeed on retry after initial failure`() {
    val instruments = listOf(createInstrument("inst-1", "AAPL", "Apple Inc"))
    stubHoldings(LightyearHoldingResponse(name = "Apple Inc", value = 5.5, instrumentId = "inst-1"))
    every { lightyearPriceClient.getInstrumentBatch(any()) } throws RuntimeException("API error") andThen instruments

    val result = service.fetchHoldingsAsDto("VUAA")

    expect(result).toHaveSize(1)
    expect(result[0].ticker).toEqual("AAPL")
    verify(exactly = 2) { lightyearPriceClient.getInstrumentBatch(any()) }
  }

  @Test
  fun `should deduplicate instrument IDs before batch fetch`() {
    stubHoldings(
      LightyearHoldingResponse(name = "Apple Inc", value = 3.0, instrumentId = "inst-1"),
      LightyearHoldingResponse(name = "Apple Preferred", value = 2.0, instrumentId = "inst-1"),
    )
    stubBatch(createInstrument("inst-1", "AAPL", "Apple Inc"))

    val result = service.fetchHoldingsAsDto("VUAA")

    expect(result).toHaveSize(2)
    verify(exactly = 1) { lightyearPriceClient.getInstrumentBatch(listOf("inst-1")) }
  }

  @Test
  fun `should handle instrument without summary`() {
    stubHoldings(LightyearHoldingResponse(name = "Company", value = 1.0, instrumentId = "inst-1"))
    stubBatch(createInstrument("inst-1", "TICK", "Company", "https://logo.png"))

    val result = service.fetchHoldingsAsDto("VUAA")

    expect(result).toHaveSize(1)
    expect(result[0].ticker).toEqual("TICK")
    expect(result[0].sector).toEqual(null)
    expect(result[0].logoUrl).toEqual("https://logo.png")
  }

  @Test
  fun `should fetch current prices for all instruments`() {
    stubSymbols("VUAA" to "uuid-1", "VWCE" to "uuid-2")
    every { lightyearPriceClient.getPrice(any()) } returns createPriceResponse(BigDecimal("100.50"))

    val result = service.fetchCurrentPrices()

    expect(result.size).toEqual(2)
    verify(exactly = 2) { lightyearPriceClient.getPrice(any()) }
  }

  @Test
  fun `should handle price fetch failure for individual instrument`() {
    stubSymbols("VUAA" to "uuid-1", "VWCE" to "uuid-2")
    every { lightyearPriceClient.getPrice("/v1/market-data/uuid-1/price") } throws RuntimeException("Failed")
    every { lightyearPriceClient.getPrice("/v1/market-data/uuid-2/price") } returns createPriceResponse(BigDecimal("50.00"))

    val result = service.fetchCurrentPrices()

    expect(result.keys).toContainExactly("VWCE")
  }

  @Test
  fun `should convert non-eur price to eur using exchange rate`() {
    stubSymbols("GOOGL:NSQ:USD" to "googl-uuid")
    every { lightyearPriceClient.getPrice("/v1/market-data/googl-uuid/price") } returns createPriceResponse(BigDecimal("356.70"), "USD")
    every {
      currencyConversionService.convertToEur(BigDecimal("356.70"), Currency.USD, LocalDate.of(2026, 7, 16))
    } returns BigDecimal("312.07")

    val result = service.fetchCurrentPrices()

    expect(result["GOOGL:NSQ:USD"]).notToEqualNull().toEqualNumerically(BigDecimal("312.07"))
  }

  @Test
  fun `should not convert price already denominated in eur`() {
    stubSymbols("VUAA:GER:EUR" to "vuaa-uuid")
    every { lightyearPriceClient.getPrice("/v1/market-data/vuaa-uuid/price") } returns createPriceResponse(BigDecimal("100.50"), "EUR")

    val result = service.fetchCurrentPrices()

    expect(result["VUAA:GER:EUR"]).notToEqualNull().toEqualNumerically(BigDecimal("100.50"))
    verify(exactly = 0) { currencyConversionService.convertToEur(any(), any(), any()) }
  }

  @Test
  fun `should filter out holdings with anomalous values exceeding threshold`() {
    stubHoldings(
      LightyearHoldingResponse(name = "Normal Company", value = 5.5, instrumentId = "inst-1"),
      LightyearHoldingResponse(name = "Corrupted Company", value = 1790494028.0, instrumentId = null),
      LightyearHoldingResponse(name = "Another Normal", value = 4.2, instrumentId = "inst-2"),
    )
    stubBatch(
      createInstrument("inst-1", "NORM", "Normal Company"),
      createInstrument("inst-2", "ANTH", "Another Normal"),
    )

    val result = service.fetchHoldingsAsDto("WTAI")

    expect(result).toHaveSize(2)
    expect(result[0].name).toEqual("Normal Company")
    expect(result[0].weight).toEqualNumerically(BigDecimal("56.701031"))
    expect(result[1].name).toEqual("Another Normal")
    expect(result[1].weight).toEqualNumerically(BigDecimal("43.298969"))
  }
}
