package ee.tenman.portfolio

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario
import ee.tenman.portfolio.binance.BinanceService
import ee.tenman.portfolio.blackrock.CsusHoldingsService
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.lightyear.LightyearHistoricalPricesService
import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.lightyear.LightyearUuidCacheService
import ee.tenman.portfolio.trading212.Trading212HoldingsService
import ee.tenman.portfolio.trading212.Trading212Service
import feign.FeignException
import feign.RetryableException
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@IntegrationTest
class RetryIT {
  @Resource
  private lateinit var binanceService: BinanceService

  @Resource
  private lateinit var lightyearPriceService: LightyearPriceService

  @Resource
  private lateinit var lightyearHistoricalPricesService: LightyearHistoricalPricesService

  @Resource
  private lateinit var lightyearUuidCacheService: LightyearUuidCacheService

  @Resource
  private lateinit var csusHoldingsService: CsusHoldingsService

  @Resource
  private lateinit var trading212Service: Trading212Service

  @Resource
  private lateinit var trading212HoldingsService: Trading212HoldingsService

  @Test
  fun `should return binance ticker price after one 503`() {
    stubFailingOnce({ get(urlPathEqualTo(TICKER_PATH)) }, okJson("""{"symbol":"BTCEUR","price":"61234.5"}"""))
    expect(binanceService.getCurrentPrice("BTCEUR")).toEqualNumerically(BigDecimal("61234.5"))
    verify(2, getRequestedFor(urlPathEqualTo(TICKER_PATH)))
  }

  @Test
  fun `should return binance hourly prices after one 503`() {
    stubFailingOnce({ get(urlPathEqualTo(KLINES_PATH)) }, okJson(KLINES))
    expect(binanceService.getHourlyPrices("BTCEUR").keys).toContainExactly(Instant.ofEpochMilli(1758499200000))
    verify(2, getRequestedFor(urlPathEqualTo(KLINES_PATH)))
  }

  @Test
  fun `should return lightyear holdings after one 503`() {
    lightyearUuidCacheService.cacheUuid("VXUS:XNAS:USD", LIGHTYEAR_UUID)
    stubFailingOnce({ lightyear(HOLDINGS) }, okJson("""[{"name":"Nestlé SA","value":2.5,"instrumentId":null}]"""))
    expect(lightyearPriceService.fetchHoldingsAsDto("VXUS:XNAS:USD").map { it.name }).toContainExactly("Nestlé SA")
    verify(2, lightyearRequests(HOLDINGS))
  }

  @Test
  fun `should return lightyear historical prices after one 503`() {
    stubFor(lightyear(chart("max")).willReturn(okJson(CHART)))
    stubFailingOnce({ lightyear(chart("5y")) }, okJson(CHART))
    expect(lightyearHistoricalPricesService.fetchHistoricalPrices(LIGHTYEAR_UUID).keys).toContainExactly(LocalDate.of(2026, 9, 21))
    verify(2, lightyearRequests(chart("5y")))
  }

  @ParameterizedTest
  @ValueSource(ints = [401, 404, 429])
  fun `should not retry lightyear holdings after a client error`(code: Int) {
    lightyearUuidCacheService.cacheUuid("VXUS:XNAS:USD", LIGHTYEAR_UUID)
    stubFor(lightyear(HOLDINGS).willReturn(status(code)))
    expect { lightyearPriceService.fetchHoldingsAsDto("VXUS:XNAS:USD") }.toThrow<FeignException.FeignClientException>()
    verify(1, lightyearRequests(HOLDINGS))
  }

  @ParameterizedTest
  @ValueSource(ints = [401, 404, 429])
  fun `should not retry lightyear instrument batches after a client error`(code: Int) {
    lightyearUuidCacheService.cacheUuid("VXUS:XNAS:USD", LIGHTYEAR_UUID)
    stubFor(lightyear(HOLDINGS).willReturn(okJson("""[{"name":"Nestlé SA","value":2.5,"instrumentId":"nestle"}]""")))
    stubFor(post(urlPathEqualTo("/lightyear/batch")).willReturn(status(code)))
    expect(lightyearPriceService.fetchHoldingsAsDto("VXUS:XNAS:USD").map { it.name }).toContainExactly("Nestlé SA")
    verify(1, postRequestedFor(urlPathEqualTo("/lightyear/batch")))
  }

  @Test
  fun `should return csus holdings after one 503`() {
    stubFailingOnce({ get(urlPathEqualTo(CSUS_PATH)) }, ok("Ticker,Name,Asset Class,Weight (%)\nAAPL,APPLE INC,Equity,6.5\n"))
    expect(csusHoldingsService.fetchHoldings().map { it.name }).toContainExactly("APPLE INC")
    verify(2, getRequestedFor(urlPathEqualTo(CSUS_PATH)))
  }

  @Test
  fun `should return trading212 prices after one 503`() {
    stubFailingOnce({ get(urlPathEqualTo(PRICES_PATH)) }, okJson(PRICES))
    val prices = trading212Service.fetchCurrentPrices(setOf("VUAA:GER:EUR"))
    expect(prices.getValue("VUAA:GER:EUR")).toEqualNumerically(BigDecimal("112.34"))
    verify(2, getRequestedFor(urlPathEqualTo(PRICES_PATH)))
  }

  @Test
  fun `should return trading212 holdings after one 503`() {
    stubFor(get(urlPathEqualTo(CATALOGUE_PATH)).willReturn(okJson("[]")))
    stubFailingOnce({ get(urlPathEqualTo(ETF_HOLDINGS_PATH)) }, okJson(ETF_HOLDINGS))
    expect(trading212HoldingsService.fetchHoldings("VUAAm_EQ").map { it.name }).toContainExactly("Société Générale")
    verify(2, getRequestedFor(urlPathEqualTo(ETF_HOLDINGS_PATH)))
  }

  @Test
  fun `should return trading212 ter after one 503`() {
    stubFailingOnce({ get(urlPathEqualTo(ETF_SUMMARY_PATH)) }, okJson("""{"expenseRatio":0.07}"""))
    expect(trading212HoldingsService.fetchTer("VUAAm_EQ")).notToEqualNull().toEqualNumerically(BigDecimal("0.07"))
    verify(2, getRequestedFor(urlPathEqualTo(ETF_SUMMARY_PATH)))
  }

  @Test
  fun `should give up on binance ticker price after three 503 responses`() {
    stubFor(get(urlPathEqualTo(TICKER_PATH)).willReturn(serviceUnavailable()))
    expect { binanceService.getCurrentPrice("BTCEUR") }.toThrow<FeignException.ServiceUnavailable>()
    verify(3, getRequestedFor(urlPathEqualTo(TICKER_PATH)))
  }

  @Test
  fun `should not retry binance ticker price after 429`() {
    stubFor(get(urlPathEqualTo(TICKER_PATH)).willReturn(status(429)))
    expect { binanceService.getCurrentPrice("BTCEUR") }.toThrow<FeignException.TooManyRequests>()
    verify(1, getRequestedFor(urlPathEqualTo(TICKER_PATH)))
  }

  @Test
  fun `should not retry binance ticker price after 429 with retry-after header`() {
    stubFor(get(urlPathEqualTo(TICKER_PATH)).willReturn(status(429).withHeader("Retry-After", "1")))
    expect { binanceService.getCurrentPrice("BTCEUR") }.toThrow<RetryableException>()
    verify(1, getRequestedFor(urlPathEqualTo(TICKER_PATH)))
  }

  private fun stubFailingOnce(
    request: () -> MappingBuilder,
    response: ResponseDefinitionBuilder,
  ) {
    stubFor(
      request()
        .inScenario(SCENARIO)
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(serviceUnavailable())
        .willSetStateTo(RECOVERED),
    )
    stubFor(
      request()
        .inScenario(SCENARIO)
        .whenScenarioStateIs(RECOVERED)
        .willReturn(response),
    )
  }

  private fun lightyear(path: String): MappingBuilder = get(urlPathEqualTo(LIGHTYEAR_PATH)).withQueryParam("path", equalTo(path))

  private fun lightyearRequests(path: String): RequestPatternBuilder =
    getRequestedFor(urlPathEqualTo(LIGHTYEAR_PATH)).withQueryParam("path", equalTo(path))

  private fun chart(range: String): String = "/v1/market-data/$LIGHTYEAR_UUID/chart?range=$range"

  companion object {
    private const val SCENARIO = "retry"
    private const val RECOVERED = "recovered"
    private const val TICKER_PATH = "/api/v3/ticker/price"
    private const val KLINES_PATH = "/api/v3/klines"
    private const val LIGHTYEAR_PATH = "/lightyear/fetch"
    private const val CSUS_PATH = "/uk/individual/products/253740/x/1472631233320.ajax"
    private const val PRICES_PATH = "/prices"
    private const val ETF_HOLDINGS_PATH = "/trading212/etf-holdings"
    private const val ETF_SUMMARY_PATH = "/trading212/etf-summary"
    private const val CATALOGUE_PATH = "/api/v0/equity/metadata/instruments"
    private const val LIGHTYEAR_UUID = "5f1c7e2a-3b9d-4c61-8e0f-a2d4b6c8e901"
    private const val HOLDINGS = "/v1/market-data/$LIGHTYEAR_UUID/fund-info/holdings"
    private const val KLINES = """[["1758499200000","61000.0","61500.0","60900.0","61234.5","12.3"]]"""
    private const val CHART = """[{"timestamp":"2026-09-21T00:00:00Z","open":10.1,"close":10.4,"high":10.5,"low":10.0,"volume":1200}]"""
    private const val PRICES = """{"data":{"VUAAm_EQ":{"b":112.34,"s":0.05,"t":"2026-09-22T10:00:00Z"}}}"""
    private const val ETF_HOLDINGS = """[{"ticker":"RETRYx_EQ","percentage":4.2,"externalName":"Société Générale"}]"""
  }
}
