package ee.tenman.portfolio.ft

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.reset
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.ninjasquad.springmockk.MockkBean
import ee.tenman.portfolio.configuration.IntegrationTest
import io.mockk.every
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@IntegrationTest
class HistoricalPricesServiceIT {
  @Resource
  private lateinit var historicalPricesService: HistoricalPricesService

  @MockkBean
  private lateinit var clock: Clock

  @Resource
  private lateinit var objectMapper: ObjectMapper

  @BeforeEach
  fun setup() {
    reset()

    val fixedClock =
      Clock.fixed(
      LocalDate.of(2025, 1, 20).atStartOfDay(ZoneId.of("UTC")).toInstant(),
      ZoneId.of("UTC"),
    )
    every { clock.instant() } returns fixedClock.instant()
    every { clock.zone } returns fixedClock.zone
  }

  @Test
  fun `should fetch XAIX prices from FT API successfully`() {
    val xaixHtml =
      """
      <tr>
        <td class="mod-ui-table__cell--text">
          <span class="mod-ui-hide-small-below">Friday, January 17, 2025</span>
        </td>
        <td>137.40</td>
        <td>139.60</td>
        <td>137.30</td>
        <td>138.80</td>
        <td>22,839</td>
      </tr>
      <tr>
        <td class="mod-ui-table__cell--text">
          <span class="mod-ui-hide-small-below">Thursday, January 16, 2025</span>
        </td>
        <td>137.90</td>
        <td>138.28</td>
        <td>137.26</td>
        <td>137.66</td>
        <td>44,583</td>
      </tr>
      """.trimIndent()

    stubFor(
      get(urlPathEqualTo("/data/equities/ajax/get-historical-prices"))
        .withQueryParam(
          "startDate",
          com.github.tomakehurst.wiremock.client.WireMock
          .matching(".*"),
            ).withQueryParam(
          "endDate",
          com.github.tomakehurst.wiremock.client.WireMock
          .matching(".*"),
            ).withQueryParam(
          "symbol",
          com.github.tomakehurst.wiremock.client.WireMock
          .equalTo("515873934"),
            ).willReturn(
          aResponse()
            .withStatus(HttpStatus.OK.value())
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withBody(objectMapper.writeValueAsString(mapOf("html" to xaixHtml))),
        ),
    )

    val result = historicalPricesService.fetchPrices("XAIX:GER:EUR")

    expect(result.size).toEqual(2)

    val jan17Data = result[LocalDate.of(2025, 1, 17)]
    expect(jan17Data).notToEqualNull()
    expect(jan17Data?.open?.compareTo(BigDecimal("137.40"))).toEqual(0)
    expect(jan17Data?.high?.compareTo(BigDecimal("139.60"))).toEqual(0)
    expect(jan17Data?.low?.compareTo(BigDecimal("137.30"))).toEqual(0)
    expect(jan17Data?.close?.compareTo(BigDecimal("138.80"))).toEqual(0)
    expect(jan17Data?.volume).toEqual(22839L)

    val jan16Data = result[LocalDate.of(2025, 1, 16)]
    expect(jan16Data).notToEqualNull()
    expect(jan16Data?.open?.compareTo(BigDecimal("137.90"))).toEqual(0)
    expect(jan16Data?.high?.compareTo(BigDecimal("138.28"))).toEqual(0)
    expect(jan16Data?.low?.compareTo(BigDecimal("137.26"))).toEqual(0)
    expect(jan16Data?.close?.compareTo(BigDecimal("137.66"))).toEqual(0)
    expect(jan16Data?.volume).toEqual(44583L)

    verify(
      com.github.tomakehurst.wiremock.client.WireMock
        .getRequestedFor(
        urlPathEqualTo("/data/equities/ajax/get-historical-prices"),
      ).withQueryParam(
        "symbol",
        com.github.tomakehurst.wiremock.client.WireMock
        .equalTo("515873934"),
          ),
    )
  }

  @Test
  fun `should handle empty response from FT API`() {
    stubFor(
      get(urlPathEqualTo("/data/equities/ajax/get-historical-prices"))
        .withQueryParam(
          "symbol",
          com.github.tomakehurst.wiremock.client.WireMock
          .equalTo("515873934"),
            ).willReturn(
          aResponse()
            .withStatus(HttpStatus.OK.value())
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withBody(objectMapper.writeValueAsString(mapOf("html" to ""))),
        ),
    )

    val result = historicalPricesService.fetchPrices("XAIX:GER:EUR")

    expect(result).toBeEmpty()
  }

  @Test
  fun `should handle 404 response from FT API`() {
    stubFor(
      get(urlPathEqualTo("/data/equities/ajax/get-historical-prices"))
        .withQueryParam(
          "symbol",
          com.github.tomakehurst.wiremock.client.WireMock
          .equalTo("515873934"),
            ).willReturn(
          aResponse()
            .withStatus(HttpStatus.NOT_FOUND.value()),
        ),
    )

    expect {
      historicalPricesService.fetchPrices("XAIX:GER:EUR")
    }.toThrow<Exception>()
  }

  @Test
  fun `should handle server error from FT API`() {
    stubFor(
      get(urlPathEqualTo("/data/equities/ajax/get-historical-prices"))
        .withQueryParam(
          "symbol",
          com.github.tomakehurst.wiremock.client.WireMock
          .equalTo("515873934"),
            ).willReturn(
          aResponse()
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value()),
        ),
    )

    expect {
      historicalPricesService.fetchPrices("XAIX:GER:EUR")
    }.toThrow<Exception>()
  }

  @Test
  fun `should validate price data consistency`() {
    val validHtml =
      """
      <tr>
        <td class="mod-ui-table__cell--text">
          <span class="mod-ui-hide-small-below">Monday, January 13, 2025</span>
        </td>
        <td>135.32</td>
        <td>135.48</td>
        <td>133.88</td>
        <td>134.48</td>
        <td>45,081</td>
      </tr>
      """.trimIndent()

    stubFor(
      get(urlPathEqualTo("/data/equities/ajax/get-historical-prices"))
        .withQueryParam(
          "symbol",
          com.github.tomakehurst.wiremock.client.WireMock
          .equalTo("515873934"),
            ).willReturn(
          aResponse()
            .withStatus(HttpStatus.OK.value())
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withBody(objectMapper.writeValueAsString(mapOf("html" to validHtml))),
        ),
    )

    val result = historicalPricesService.fetchPrices("XAIX:GER:EUR")

    expect(result.size).toEqual(1)
    val data = result[LocalDate.of(2025, 1, 13)]!!

    expect(data.low).toBeLessThanOrEqualTo(data.open)
    expect(data.low).toBeLessThanOrEqualTo(data.close)
    expect(data.low).toBeLessThanOrEqualTo(data.high)
    expect(data.high).toBeGreaterThanOrEqualTo(data.open)
    expect(data.high).toBeGreaterThanOrEqualTo(data.close)
    expect(data.high).toBeGreaterThanOrEqualTo(data.low)
  }

  @Test
  fun `should handle volume with k suffix correctly`() {
    val htmlWithKVolume =
      """
      <tr>
        <td class="mod-ui-table__cell--text">
          <span class="mod-ui-hide-small-below">Tuesday, January 07, 2025</span>
        </td>
        <td>138.40</td>
        <td>139.48</td>
        <td>137.10</td>
        <td>137.86</td>
        <td>74.64k</td>
      </tr>
      """.trimIndent()

    stubFor(
      get(urlPathEqualTo("/data/equities/ajax/get-historical-prices"))
        .withQueryParam(
          "symbol",
          com.github.tomakehurst.wiremock.client.WireMock
          .equalTo("515873934"),
            ).willReturn(
          aResponse()
            .withStatus(HttpStatus.OK.value())
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withBody(objectMapper.writeValueAsString(mapOf("html" to htmlWithKVolume))),
        ),
    )

    val result = historicalPricesService.fetchPrices("XAIX:GER:EUR")

    expect(result.size).toEqual(1)
    val data = result[LocalDate.of(2025, 1, 7)]!!
    expect(data.volume).toEqual(74640L)
  }

  @Test
  fun `should verify correct ticker mapping for all configured symbols`() {
    val testCases =
      mapOf(
        "QDVE.DEX" to "93501088",
        "QDVE:GER:EUR" to "93500326",
        "XAIX:GER:EUR" to "515873934",
      )

    testCases.forEach { (symbol, expectedTickerId) ->
      stubFor(
        get(urlPathEqualTo("/data/equities/ajax/get-historical-prices"))
          .withQueryParam(
            "startDate",
            com.github.tomakehurst.wiremock.client.WireMock
            .matching(".*"),
              ).withQueryParam(
            "endDate",
            com.github.tomakehurst.wiremock.client.WireMock
            .matching(".*"),
              ).withQueryParam(
            "symbol",
            com.github.tomakehurst.wiremock.client.WireMock
            .equalTo(expectedTickerId),
              ).willReturn(
            aResponse()
              .withStatus(HttpStatus.OK.value())
              .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
              .withBody(objectMapper.writeValueAsString(mapOf("html" to ""))),
          ),
      )

      historicalPricesService.fetchPrices(symbol)

      verify(
        getRequestedFor(urlPathEqualTo("/data/equities/ajax/get-historical-prices"))
          .withQueryParam(
            "symbol",
            com.github.tomakehurst.wiremock.client.WireMock
            .equalTo(expectedTickerId),
              ),
      )
    }
  }
}
