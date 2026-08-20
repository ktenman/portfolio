package ee.tenman.portfolio.lightyear

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.Instrument
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal
import java.util.Optional

class LightyearUuidResolutionTest : LightyearPriceServiceTestBase() {
  @ParameterizedTest
  @CsvSource(
    "VUAA:GER:EUR, 1eda4008-c9e6-6bde-b60a-654bcfbd8ac3",
    "VWCE, 1eda0a07-10b3-63e0-b568-6deedaa217e7",
  )
  fun `should fetch holdings with the UUID configured for the symbol`(
    symbol: String,
    uuid: String,
  ) {
    val holdings = listOf(LightyearHoldingResponse(name = "Company", value = 1.0, instrumentId = null))
    val holdingsPath = "/v1/market-data/$uuid/fund-info/holdings"
    every { properties.findUuidBySymbol(symbol) } returns uuid
    every { lightyearPriceClient.getHoldings(holdingsPath) } returns holdings

    val result = service.fetchHoldingsAsDto(symbol)

    expect(result).toHaveSize(1)
    verify { lightyearPriceClient.getHoldings(holdingsPath) }
  }

  @Test
  fun `should resolve UUID from web lookup when not in config`() {
    stubWebLookup("NEWETF:GER:EUR", "NEWETF:XETRA", "web-uuid-123")
    every { instrumentRepository.findBySymbol("NEWETF:GER:EUR") } returns Optional.empty()

    val result = service.resolveUuid("NEWETF:GER:EUR")

    expect(result).toEqual("web-uuid-123")
    verify { lightyearPriceClient.lookupUuid("NEWETF:XETRA") }
    verify { instrumentService.updateProviderExternalId("NEWETF:GER:EUR", "web-uuid-123") }
    verify { uuidCacheService.cacheUuid("NEWETF:GER:EUR", "web-uuid-123") }
  }

  @Test
  fun `should use cached UUID on subsequent calls`() {
    stubWebLookup("CACHED:GER:EUR", "CACHED:XETRA", "cached-uuid")
    every { uuidCacheService.getCachedUuid("CACHED:GER:EUR") } returns null andThen "cached-uuid"
    every { instrumentRepository.findBySymbol("CACHED:GER:EUR") } returns Optional.empty()

    service.resolveUuid("CACHED:GER:EUR")
    val result = service.resolveUuid("CACHED:GER:EUR")

    expect(result).toEqual("cached-uuid")
    verify(exactly = 1) { lightyearPriceClient.lookupUuid("CACHED:XETRA") }
    verify(exactly = 1) { uuidCacheService.cacheUuid("CACHED:GER:EUR", "cached-uuid") }
  }

  @Test
  fun `should return null when web lookup fails`() {
    every { properties.findUuidBySymbol("FAIL:GER:EUR") } returns null
    every { instrumentRepository.findBySymbol("FAIL:GER:EUR") } returns Optional.empty()
    every { lightyearPriceClient.lookupUuid("FAIL:XETRA") } throws RuntimeException("Not found")

    val result = service.resolveUuid("FAIL:GER:EUR")

    expect(result).toEqual(null)
  }

  @ParameterizedTest
  @CsvSource("GER, XETRA", "AEX, AMS")
  fun `should convert the exchange suffix to its Lightyear name for lookup`(
    exchange: String,
    lightyearExchange: String,
  ) {
    val symbol = "TEST:$exchange:EUR"
    val lookupSymbol = "TEST:$lightyearExchange"
    stubWebLookup(symbol, lookupSymbol, "uuid-123")

    service.lookupUuidFromWeb(symbol)

    verify { lightyearPriceClient.lookupUuid(lookupSymbol) }
  }

  @Test
  fun `should prefer config UUID over web lookup`() {
    every { properties.findUuidBySymbol("CONFIG:GER:EUR") } returns "config-uuid"

    val result = service.resolveUuid("CONFIG:GER:EUR")

    expect(result).toEqual("config-uuid")
    verify(exactly = 0) { lightyearPriceClient.lookupUuid(any()) }
  }

  @Test
  fun `should resolve UUID from database instead of web lookup when not in config`() {
    val instrument = mockk<Instrument>()
    every { instrument.providerExternalId } returns "db-uuid-456"
    every { properties.findUuidBySymbol("DBETF:GER:EUR") } returns null
    every { instrumentRepository.findBySymbol("DBETF:GER:EUR") } returns Optional.of(instrument)

    val result = service.resolveUuid("DBETF:GER:EUR")

    expect(result).toEqual("db-uuid-456")
    verify(exactly = 0) { lightyearPriceClient.lookupUuid(any()) }
    verify(exactly = 0) { instrumentService.updateProviderExternalId(any(), any()) }
  }

  @Test
  fun `should fall back to web lookup when database has no external id`() {
    val instrument = mockk<Instrument>()
    every { instrument.providerExternalId } returns null
    stubWebLookup("NOID:GER:EUR", "NOID:XETRA", "new-uuid")
    every { instrumentRepository.findBySymbol("NOID:GER:EUR") } returns Optional.of(instrument)

    val result = service.resolveUuid("NOID:GER:EUR")

    expect(result).toEqual("new-uuid")
    verify { lightyearPriceClient.lookupUuid("NOID:XETRA") }
    verify { instrumentService.updateProviderExternalId("NOID:GER:EUR", "new-uuid") }
  }

  @Test
  fun `should fetch fund info and return ter and fundCurrency`() {
    val response = LightyearFundInfoResponse(ter = BigDecimal("0.40"), fundCurrency = "USD")
    every { properties.findUuidBySymbol("VUAA") } returns "test-uuid"
    every { lightyearPriceClient.getFundInfo("/v1/market-data/test-uuid/fund-info") } returns response

    val result = service.fetchFundInfo("VUAA")

    expect(result!!.ter).notToEqualNull().toEqualNumerically(BigDecimal("0.40"))
    expect(result.fundCurrency).toEqual(Currency.USD)
  }

  @Test
  fun `should return null when fund info fetch fails`() {
    every { properties.findUuidBySymbol("VUAA") } returns "test-uuid"
    every { lightyearPriceClient.getFundInfo(any()) } throws RuntimeException("API error")

    val result = service.fetchFundInfo("VUAA")

    expect(result).toEqual(null)
  }

  @Test
  fun `should return null when no UUID found for fund info`() {
    every { properties.findUuidBySymbol("UNKNOWN") } returns null
    every { instrumentRepository.findBySymbol("UNKNOWN") } returns Optional.empty()
    every { lightyearPriceClient.lookupUuid(any()) } throws RuntimeException("Not found")

    val result = service.fetchFundInfo("UNKNOWN")

    expect(result).toEqual(null)
  }

  @Test
  fun `should return data with null ter and null fundCurrency when both missing`() {
    val response = LightyearFundInfoResponse(ter = null, aum = BigDecimal("1000"), fundCurrency = null)
    every { properties.findUuidBySymbol("STOCK") } returns "stock-uuid"
    every { lightyearPriceClient.getFundInfo("/v1/market-data/stock-uuid/fund-info") } returns response

    val result = service.fetchFundInfo("STOCK")

    expect(result!!.ter).toEqual(null)
    expect(result.fundCurrency).toEqual(null)
  }

  private fun stubWebLookup(
    symbol: String,
    lookupSymbol: String,
    uuid: String,
  ) {
    every { properties.findUuidBySymbol(symbol) } returns null
    every { lightyearPriceClient.lookupUuid(lookupSymbol) } returns LightyearUuidLookupResponse(lookupSymbol, uuid)
    every { instrumentService.updateProviderExternalId(symbol, uuid) } just runs
  }
}
