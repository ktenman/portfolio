package ee.tenman.portfolio.lightyear

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.LightyearScrapingProperties
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.instrument.InstrumentService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Optional

class LightyearPriceServiceTest {
  private val lightyearPriceClient = mockk<LightyearPriceClient>()
  private val properties = mockk<LightyearScrapingProperties>()
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val instrumentService = mockk<InstrumentService>()
  private val uuidCacheService = mockk<LightyearUuidCacheService>()
  private lateinit var service: LightyearPriceService

  @BeforeEach
  fun setUp() {
    every { uuidCacheService.getCachedUuid(any()) } returns null
    every { uuidCacheService.cacheUuid(any(), any()) } answers { secondArg() }
    setupExchangeMapping()
    service = LightyearPriceService(lightyearPriceClient, properties, instrumentRepository, instrumentService, uuidCacheService)
  }

  private fun setupExchangeMapping() {
    every { properties.convertExchangeToLightyear("GER") } returns "XETRA"
    every { properties.convertExchangeToLightyear("AEX") } returns "AMS"
    every { properties.convertExchangeToLightyear("MIL") } returns "MIL"
    every { properties.convertExchangeToLightyear("LON") } returns "LSE"
    every { properties.convertExchangeToLightyear(match { it !in listOf("GER", "AEX", "MIL", "LON") }) } answers { firstArg() }
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
    val holdings =
      listOf(
      LightyearHoldingResponse(name = "Apple Inc", value = 5.5, instrumentId = "inst-1"),
      LightyearHoldingResponse(name = "Microsoft Corp", value = 4.2, instrumentId = "inst-2"),
    )
    val instruments =
      listOf(
      LightyearInstrumentResponse(
        id = "inst-1",
        symbol = "AAPL",
        name = "Apple Inc",
        exchange = "NASDAQ",
        logo = "https://logo.com/aapl.png",
        summary = LightyearInstrumentSummary(sector = "Technology"),
      ),
      LightyearInstrumentResponse(
        id = "inst-2",
        symbol = "MSFT",
        name = "Microsoft Corp",
        exchange = "NASDAQ",
        logo = "https://logo.com/msft.png",
        summary = LightyearInstrumentSummary(sector = "Software"),
      ),
    )
    every { properties.findUuidBySymbol("VUAA") } returns "test-uuid"
    every { lightyearPriceClient.getHoldings(any()) } returns holdings
    every { lightyearPriceClient.getInstrumentBatch(listOf("inst-1", "inst-2")) } returns instruments

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
    val holdings =
      listOf(
      LightyearHoldingResponse(name = "Unknown Company", value = 1.5, instrumentId = null),
    )
    every { properties.findUuidBySymbol("VUAA") } returns "test-uuid"
    every { lightyearPriceClient.getHoldings(any()) } returns holdings

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
    every { properties.findUuidBySymbol("VUAA") } returns "test-uuid"
    every { lightyearPriceClient.getHoldings(any()) } returns emptyList()

    val result = service.fetchHoldingsAsDto("VUAA")

    expect(result).toBeEmpty()
    verify(exactly = 0) { lightyearPriceClient.getInstrumentBatch(any()) }
  }

  @Test
  fun `should handle batch fetch failure gracefully after retries`() {
    val holdings =
      listOf(
      LightyearHoldingResponse(name = "Apple Inc", value = 5.5, instrumentId = "inst-1"),
    )
    every { properties.findUuidBySymbol("VUAA") } returns "test-uuid"
    every { lightyearPriceClient.getHoldings(any()) } returns holdings
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
    val holdings =
      listOf(
      LightyearHoldingResponse(name = "Apple Inc", value = 5.5, instrumentId = "inst-1"),
    )
    val instruments =
      listOf(
      LightyearInstrumentResponse(
        id = "inst-1",
        symbol = "AAPL",
        name = "Apple Inc",
        exchange = "NASDAQ",
        logo = null,
        summary = null,
      ),
    )
    every { properties.findUuidBySymbol("VUAA") } returns "test-uuid"
    every { lightyearPriceClient.getHoldings(any()) } returns holdings
    every { lightyearPriceClient.getInstrumentBatch(any()) } throws RuntimeException("API error") andThen instruments

    val result = service.fetchHoldingsAsDto("VUAA")

    expect(result).toHaveSize(1)
    expect(result[0].ticker).toEqual("AAPL")
    verify(exactly = 2) { lightyearPriceClient.getInstrumentBatch(any()) }
  }

  @Test
  fun `should find UUID by full symbol`() {
    val holdings =
      listOf(
      LightyearHoldingResponse(name = "Company", value = 1.0, instrumentId = null),
    )
    every { properties.findUuidBySymbol("VUAA:GER:EUR") } returns "1eda4008-c9e6-6bde-b60a-654bcfbd8ac3"
    every { lightyearPriceClient.getHoldings("/v1/market-data/1eda4008-c9e6-6bde-b60a-654bcfbd8ac3/fund-info/holdings") } returns holdings

    val result = service.fetchHoldingsAsDto("VUAA:GER:EUR")

    expect(result).toHaveSize(1)
    verify { lightyearPriceClient.getHoldings("/v1/market-data/1eda4008-c9e6-6bde-b60a-654bcfbd8ac3/fund-info/holdings") }
  }

  @Test
  fun `should find UUID by short symbol prefix`() {
    val holdings =
      listOf(
      LightyearHoldingResponse(name = "Company", value = 1.0, instrumentId = null),
    )
    every { properties.findUuidBySymbol("VWCE") } returns "1eda0a07-10b3-63e0-b568-6deedaa217e7"
    every { lightyearPriceClient.getHoldings("/v1/market-data/1eda0a07-10b3-63e0-b568-6deedaa217e7/fund-info/holdings") } returns holdings

    val result = service.fetchHoldingsAsDto("VWCE")

    expect(result).toHaveSize(1)
    verify { lightyearPriceClient.getHoldings("/v1/market-data/1eda0a07-10b3-63e0-b568-6deedaa217e7/fund-info/holdings") }
  }

  @Test
  fun `should deduplicate instrument IDs before batch fetch`() {
    val holdings =
      listOf(
      LightyearHoldingResponse(name = "Apple Inc", value = 3.0, instrumentId = "inst-1"),
      LightyearHoldingResponse(name = "Apple Preferred", value = 2.0, instrumentId = "inst-1"),
    )
    val instruments =
      listOf(
      LightyearInstrumentResponse(
        id = "inst-1",
        symbol = "AAPL",
        name = "Apple Inc",
        exchange = "NASDAQ",
        logo = null,
        summary = null,
      ),
    )
    every { properties.findUuidBySymbol("VUAA") } returns "test-uuid"
    every { lightyearPriceClient.getHoldings(any()) } returns holdings
    every { lightyearPriceClient.getInstrumentBatch(listOf("inst-1")) } returns instruments

    val result = service.fetchHoldingsAsDto("VUAA")

    expect(result).toHaveSize(2)
    verify(exactly = 1) { lightyearPriceClient.getInstrumentBatch(listOf("inst-1")) }
  }

  @Test
  fun `should handle instrument without summary`() {
    val holdings =
      listOf(
      LightyearHoldingResponse(name = "Company", value = 1.0, instrumentId = "inst-1"),
    )
    val instruments =
      listOf(
      LightyearInstrumentResponse(
        id = "inst-1",
        symbol = "TICK",
        name = "Company",
        exchange = "NYSE",
        logo = "https://logo.png",
        summary = null,
      ),
    )
    every { properties.findUuidBySymbol("VUAA") } returns "test-uuid"
    every { lightyearPriceClient.getHoldings(any()) } returns holdings
    every { lightyearPriceClient.getInstrumentBatch(any()) } returns instruments

    val result = service.fetchHoldingsAsDto("VUAA")

    expect(result).toHaveSize(1)
    expect(result[0].ticker).toEqual("TICK")
    expect(result[0].sector).toEqual(null)
    expect(result[0].logoUrl).toEqual("https://logo.png")
  }

  @Test
  fun `should fetch current prices for all instruments`() {
    val symbols = listOf("VUAA", "VWCE")
    every { properties.getAllSymbols() } returns symbols
    every { properties.findUuidBySymbol("VUAA") } returns "uuid-1"
    every { properties.findUuidBySymbol("VWCE") } returns "uuid-2"
    every { lightyearPriceClient.getPrice(any()) } returns createPriceResponse(BigDecimal("100.50"))

    val result = service.fetchCurrentPrices()

    expect(result.size).toEqual(2)
    verify(exactly = 2) { lightyearPriceClient.getPrice(any()) }
  }

  @Test
  fun `should handle price fetch failure for individual instrument`() {
    val symbols = listOf("VUAA", "VWCE")
    every { properties.getAllSymbols() } returns symbols
    every { properties.findUuidBySymbol("VUAA") } returns "uuid-1"
    every { properties.findUuidBySymbol("VWCE") } returns "uuid-2"
    every { lightyearPriceClient.getPrice("/v1/market-data/uuid-1/price") } throws RuntimeException("Failed")
    every { lightyearPriceClient.getPrice("/v1/market-data/uuid-2/price") } returns createPriceResponse(BigDecimal("50.00"))

    val result = service.fetchCurrentPrices()

    expect(result.size).toEqual(1)
    expect(result.containsKey("VUAA")).toEqual(false)
    expect(result.containsKey("VWCE")).toEqual(true)
  }

  @Test
  fun `should resolve UUID from web lookup when not in config`() {
    every { properties.findUuidBySymbol("NEWETF:GER:EUR") } returns null
    every { instrumentRepository.findBySymbol("NEWETF:GER:EUR") } returns Optional.empty()
    every { lightyearPriceClient.lookupUuid("NEWETF:XETRA") } returns LightyearUuidLookupResponse("NEWETF:XETRA", "web-uuid-123")
    every { instrumentService.updateProviderExternalId("NEWETF:GER:EUR", "web-uuid-123") } just runs

    val result = service.resolveUuid("NEWETF:GER:EUR")

    expect(result).toEqual("web-uuid-123")
    verify { lightyearPriceClient.lookupUuid("NEWETF:XETRA") }
    verify { instrumentService.updateProviderExternalId("NEWETF:GER:EUR", "web-uuid-123") }
    verify { uuidCacheService.cacheUuid("NEWETF:GER:EUR", "web-uuid-123") }
  }

  @Test
  fun `should use cached UUID on subsequent calls`() {
    every { properties.findUuidBySymbol("CACHED:GER:EUR") } returns null
    every { uuidCacheService.getCachedUuid("CACHED:GER:EUR") } returns null andThen "cached-uuid"
    every { instrumentRepository.findBySymbol("CACHED:GER:EUR") } returns Optional.empty()
    every { lightyearPriceClient.lookupUuid("CACHED:XETRA") } returns LightyearUuidLookupResponse("CACHED:XETRA", "cached-uuid")
    every { instrumentService.updateProviderExternalId("CACHED:GER:EUR", "cached-uuid") } just runs

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

  @Test
  fun `should convert GER exchange to XETRA for lookup`() {
    every { properties.findUuidBySymbol("TEST:GER:EUR") } returns null
    every { lightyearPriceClient.lookupUuid("TEST:XETRA") } returns LightyearUuidLookupResponse("TEST:XETRA", "uuid-123")
    every { instrumentService.updateProviderExternalId("TEST:GER:EUR", "uuid-123") } just runs

    service.lookupUuidFromWeb("TEST:GER:EUR")

    verify { lightyearPriceClient.lookupUuid("TEST:XETRA") }
  }

  @Test
  fun `should convert AEX exchange to AMS for lookup`() {
    every { properties.findUuidBySymbol("TEST:AEX:EUR") } returns null
    every { lightyearPriceClient.lookupUuid("TEST:AMS") } returns LightyearUuidLookupResponse("TEST:AMS", "uuid-456")
    every { instrumentService.updateProviderExternalId("TEST:AEX:EUR", "uuid-456") } just runs

    service.lookupUuidFromWeb("TEST:AEX:EUR")

    verify { lightyearPriceClient.lookupUuid("TEST:AMS") }
  }

  @Test
  fun `should prefer config UUID over web lookup`() {
    every { properties.findUuidBySymbol("CONFIG:GER:EUR") } returns "config-uuid"

    val result = service.resolveUuid("CONFIG:GER:EUR")

    expect(result).toEqual("config-uuid")
    verify(exactly = 0) { lightyearPriceClient.lookupUuid(any()) }
  }

  @Test
  fun `should resolve UUID from database when not in config`() {
    val instrument = mockk<Instrument>()
    every { instrument.providerExternalId } returns "db-uuid-456"
    every { properties.findUuidBySymbol("DBETF:GER:EUR") } returns null
    every { instrumentRepository.findBySymbol("DBETF:GER:EUR") } returns Optional.of(instrument)

    val result = service.resolveUuid("DBETF:GER:EUR")

    expect(result).toEqual("db-uuid-456")
    verify(exactly = 0) { lightyearPriceClient.lookupUuid(any()) }
  }

  @Test
  fun `should prefer database UUID over web lookup`() {
    val instrument = mockk<Instrument>()
    every { instrument.providerExternalId } returns "db-uuid"
    every { properties.findUuidBySymbol("PREF:GER:EUR") } returns null
    every { instrumentRepository.findBySymbol("PREF:GER:EUR") } returns Optional.of(instrument)

    val result = service.resolveUuid("PREF:GER:EUR")

    expect(result).toEqual("db-uuid")
    verify(exactly = 0) { lightyearPriceClient.lookupUuid(any()) }
    verify(exactly = 0) { instrumentService.updateProviderExternalId(any(), any()) }
  }

  @Test
  fun `should fall back to web lookup when database has no external id`() {
    val instrument = mockk<Instrument>()
    every { instrument.providerExternalId } returns null
    every { properties.findUuidBySymbol("NOID:GER:EUR") } returns null
    every { instrumentRepository.findBySymbol("NOID:GER:EUR") } returns Optional.of(instrument)
    every { lightyearPriceClient.lookupUuid("NOID:XETRA") } returns LightyearUuidLookupResponse("NOID:XETRA", "new-uuid")
    every { instrumentService.updateProviderExternalId("NOID:GER:EUR", "new-uuid") } just runs

    val result = service.resolveUuid("NOID:GER:EUR")

    expect(result).toEqual("new-uuid")
    verify { lightyearPriceClient.lookupUuid("NOID:XETRA") }
    verify { instrumentService.updateProviderExternalId("NOID:GER:EUR", "new-uuid") }
  }

  private fun createPriceResponse(price: BigDecimal) =
    LightyearPriceResponse(
      timestamp = "2025-01-01T00:00:00Z",
      price = price,
      change = BigDecimal.ZERO,
      changePercent = BigDecimal.ZERO,
      currency = "EUR",
    )

  @Test
  fun `should fetch fund info and return TER`() {
    val response = LightyearFundInfoResponse(ter = BigDecimal("0.40"))
    every { properties.findUuidBySymbol("VUAA") } returns "test-uuid"
    every { lightyearPriceClient.getFundInfo("/v1/market-data/test-uuid/fund-info") } returns response

    val result = service.fetchFundInfo("VUAA")

    expect(result).notToEqualNull().toEqualNumerically(BigDecimal("0.40"))
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
  fun `should return null when fund info has no TER`() {
    val response = LightyearFundInfoResponse(ter = null, aum = BigDecimal("1000"))
    every { properties.findUuidBySymbol("STOCK") } returns "stock-uuid"
    every { lightyearPriceClient.getFundInfo("/v1/market-data/stock-uuid/fund-info") } returns response

    val result = service.fetchFundInfo("STOCK")

    expect(result).toEqual(null)
  }

  @Test
  fun `should filter out holdings with anomalous values exceeding threshold`() {
    val holdings =
      listOf(
        LightyearHoldingResponse(name = "Normal Company", value = 5.5, instrumentId = "inst-1"),
        LightyearHoldingResponse(name = "Corrupted Company", value = 1790494028.0, instrumentId = null),
        LightyearHoldingResponse(name = "Another Normal", value = 4.2, instrumentId = "inst-2"),
      )
    val instruments =
      listOf(
        LightyearInstrumentResponse(
          id = "inst-1",
          symbol = "NORM",
          name = "Normal Company",
          exchange = "NYSE",
          logo = null,
          summary = null,
        ),
        LightyearInstrumentResponse(
          id = "inst-2",
          symbol = "ANTH",
          name = "Another Normal",
          exchange = "NYSE",
          logo = null,
          summary = null,
        ),
      )
    every { properties.findUuidBySymbol("WTAI") } returns "wtai-uuid"
    every { lightyearPriceClient.getHoldings(any()) } returns holdings
    every { lightyearPriceClient.getInstrumentBatch(listOf("inst-1", "inst-2")) } returns instruments

    val result = service.fetchHoldingsAsDto("WTAI")

    expect(result).toHaveSize(2)
    expect(result[0].name).toEqual("Normal Company")
    expect(result[0].weight).toEqualNumerically(BigDecimal("56.701031"))
    expect(result[1].name).toEqual("Another Normal")
    expect(result[1].weight).toEqualNumerically(BigDecimal("43.298969"))
  }
}
