package ee.tenman.portfolio.ft

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.reset
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import ee.tenman.portfolio.configuration.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.annotation.Resource

@IntegrationTest
class HistoricalPricesServiceIT {
  @Resource
  private lateinit var historicalPricesService: HistoricalPricesService

  @MockitoBean
  private lateinit var clock: Clock

  @Resource
  private lateinit var objectMapper: ObjectMapper

  @BeforeEach
  fun setup() {
    // Reset WireMock before each test
    reset()

    // Set clock to January 20, 2025 to make date range include test data
    val fixedClock =
      Clock.fixed(
      LocalDate.of(2025, 1, 20).atStartOfDay(ZoneId.of("UTC")).toInstant(),
      ZoneId.of("UTC"),
    )
    whenever(clock.instant()).thenReturn(fixedClock.instant())
    whenever(clock.zone).thenReturn(fixedClock.zone)
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

    assertThat(result).hasSize(2)

    val jan17Data = result[LocalDate.of(2025, 1, 17)]
    assertThat(jan17Data).isNotNull
    assertThat(jan17Data?.open).isEqualTo(BigDecimal("137.40"))
    assertThat(jan17Data?.high).isEqualTo(BigDecimal("139.60"))
    assertThat(jan17Data?.low).isEqualTo(BigDecimal("137.30"))
    assertThat(jan17Data?.close).isEqualTo(BigDecimal("138.80"))
    assertThat(jan17Data?.volume).isEqualTo(22839L)

    val jan16Data = result[LocalDate.of(2025, 1, 16)]
    assertThat(jan16Data).isNotNull
    assertThat(jan16Data?.open).isEqualTo(BigDecimal("137.90"))
    assertThat(jan16Data?.high).isEqualTo(BigDecimal("138.28"))
    assertThat(jan16Data?.low).isEqualTo(BigDecimal("137.26"))
    assertThat(jan16Data?.close).isEqualTo(BigDecimal("137.66"))
    assertThat(jan16Data?.volume).isEqualTo(44583L)

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

    assertThat(result).isEmpty()
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

    assertThatThrownBy {
      historicalPricesService.fetchPrices("XAIX:GER:EUR")
    }.isInstanceOf(Exception::class.java)
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

    assertThatThrownBy {
      historicalPricesService.fetchPrices("XAIX:GER:EUR")
    }.isInstanceOf(Exception::class.java)
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

    assertThat(result).hasSize(1)
    val data = result[LocalDate.of(2025, 1, 13)]!!

    assertThat(data.low).isLessThanOrEqualTo(data.open)
    assertThat(data.low).isLessThanOrEqualTo(data.close)
    assertThat(data.low).isLessThanOrEqualTo(data.high)
    assertThat(data.high).isGreaterThanOrEqualTo(data.open)
    assertThat(data.high).isGreaterThanOrEqualTo(data.close)
    assertThat(data.high).isGreaterThanOrEqualTo(data.low)
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

    assertThat(result).hasSize(1)
    val data = result[LocalDate.of(2025, 1, 7)]!!
    assertThat(data.volume).isEqualTo(74640L)
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
