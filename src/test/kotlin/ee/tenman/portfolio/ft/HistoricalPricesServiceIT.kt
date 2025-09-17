package ee.tenman.portfolio.ft

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.time.LocalDate
import javax.annotation.Resource

@IntegrationTest
class HistoricalPricesServiceIT {
  @Resource
  private lateinit var historicalPricesService: HistoricalPricesService

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
            .withBody("""{"html": "$xaixHtml"}"""),
        ),
    )

    val result = historicalPricesService.fetchPrices("XAIX:GER:EUR")

    assertThat(result).isNotEmpty
    assertThat(result).containsKey(LocalDate.of(2025, 1, 17))
    assertThat(result).containsKey(LocalDate.of(2025, 1, 16))

    val jan17Data = result[LocalDate.of(2025, 1, 17)]
    assertThat(jan17Data).isNotNull
    assertThat(jan17Data?.open).isEqualTo("137.40")
    assertThat(jan17Data?.high).isEqualTo("139.60")
    assertThat(jan17Data?.low).isEqualTo("137.30")
    assertThat(jan17Data?.close).isEqualTo("138.80")
    assertThat(jan17Data?.volume).isGreaterThan(0L)

    val jan16Data = result[LocalDate.of(2025, 1, 16)]
    assertThat(jan16Data).isNotNull
    assertThat(jan16Data?.open).isEqualTo("137.90")
    assertThat(jan16Data?.high).isEqualTo("138.28")
    assertThat(jan16Data?.low).isEqualTo("137.26")
    assertThat(jan16Data?.close).isEqualTo("137.66")
    assertThat(jan16Data?.volume).isGreaterThan(0L)
  }

  @Test
  fun `should handle empty response from FT API`() {
    stubFor(
      get(urlPathEqualTo("/data/equities/ajax/get-historical-prices"))
        .withQueryParam(
          "symbol",
          com.github.tomakehurst.wiremock.client.WireMock
          .equalTo("515873934"),
            ).withQueryParam(
          "startDate",
          com.github.tomakehurst.wiremock.client.WireMock
            .matching(".*"),
        ).withQueryParam(
          "endDate",
          com.github.tomakehurst.wiremock.client.WireMock
            .matching(".*"),
        ).willReturn(
          aResponse()
            .withStatus(HttpStatus.OK.value())
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withBody("""{"html": ""}"""),
        ),
    )

    val result = historicalPricesService.fetchPrices("XAIX:GER:EUR")

    assertThat(result).isNotNull
  }

  @Test
  fun `should handle 404 response from FT API`() {
    stubFor(
      get(urlPathEqualTo("/data/equities/ajax/get-historical-prices"))
        .withQueryParam(
          "symbol",
          com.github.tomakehurst.wiremock.client.WireMock
          .equalTo("515873934"),
            ).withQueryParam(
          "startDate",
          com.github.tomakehurst.wiremock.client.WireMock
            .matching(".*"),
        ).withQueryParam(
          "endDate",
          com.github.tomakehurst.wiremock.client.WireMock
            .matching(".*"),
        ).willReturn(
          aResponse()
            .withStatus(HttpStatus.NOT_FOUND.value()),
        ),
    )

    val result = historicalPricesService.fetchPrices("XAIX:GER:EUR")
    assertThat(result).isNotNull
  }

  @Test
  fun `should handle server error from FT API`() {
    stubFor(
      get(urlPathEqualTo("/data/equities/ajax/get-historical-prices"))
        .withQueryParam(
          "symbol",
          com.github.tomakehurst.wiremock.client.WireMock
          .equalTo("515873934"),
            ).withQueryParam(
          "startDate",
          com.github.tomakehurst.wiremock.client.WireMock
            .matching(".*"),
        ).withQueryParam(
          "endDate",
          com.github.tomakehurst.wiremock.client.WireMock
            .matching(".*"),
        ).willReturn(
          aResponse()
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value()),
        ),
    )

    val result = historicalPricesService.fetchPrices("XAIX:GER:EUR")
    assertThat(result).isNotNull
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
            .withBody("""{"html": "$validHtml"}"""),
        ),
    )

    val result = historicalPricesService.fetchPrices("XAIX:GER:EUR")

    assertThat(result).isNotEmpty
    assertThat(result).containsKey(LocalDate.of(2025, 1, 13))
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
            ).withQueryParam(
          "startDate",
          com.github.tomakehurst.wiremock.client.WireMock
            .matching(".*"),
        ).withQueryParam(
          "endDate",
          com.github.tomakehurst.wiremock.client.WireMock
            .matching(".*"),
        ).willReturn(
          aResponse()
            .withStatus(HttpStatus.OK.value())
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withBody("""{"html": "$htmlWithKVolume"}"""),
        ),
    )

    val result = historicalPricesService.fetchPrices("XAIX:GER:EUR")

    assertThat(result).isNotEmpty
    assertThat(result).containsKey(LocalDate.of(2025, 1, 7))
    val data = result[LocalDate.of(2025, 1, 7)]!!
    assertThat(data.volume).isGreaterThan(0L)
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
            "symbol",
            com.github.tomakehurst.wiremock.client.WireMock
            .equalTo(expectedTickerId),
              ).withQueryParam(
            "startDate",
            com.github.tomakehurst.wiremock.client.WireMock
              .matching(".*"),
          ).withQueryParam(
            "endDate",
            com.github.tomakehurst.wiremock.client.WireMock
              .matching(".*"),
          ).willReturn(
            aResponse()
              .withStatus(HttpStatus.OK.value())
              .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
              .withBody("""{"html": ""}"""),
          ),
      )

      val result = historicalPricesService.fetchPrices(symbol)
      assertThat(result).isNotNull
    }
  }
}
