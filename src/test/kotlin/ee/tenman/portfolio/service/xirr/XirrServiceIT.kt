package ee.tenman.portfolio.service.xirr

import com.github.tomakehurst.wiremock.client.WireMock.*
import ee.tenman.portfolio.configuration.IntegrationTest
import jakarta.annotation.Resource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE

@IntegrationTest
class XirrServiceIT {

  @Resource
  lateinit var xirrService: XirrService

  @Test
  fun calculateStockXirr() {
    stubFor(
      get(urlPathEqualTo("/query"))
        .withQueryParam("function", equalTo("SYMBOL_SEARCH"))
        .withQueryParam("keywords", equalTo("QDVE.DEX"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBodyFile("symbol-search-response.json")
        )
    )

    stubFor(
      get(urlPathEqualTo("/query"))
        .withQueryParam("function", equalTo("TIME_SERIES_MONTHLY"))
        .withQueryParam("symbol", equalTo("QDVE.DEX"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBodyFile("alpha-vantage-monthly-response.json")
        )
    )

    val calculateStockXirr = xirrService.calculateStockXirr("QDVE.DEX")

    assertThat(calculateStockXirr).isCloseTo(1.2666725806568464, within(1e-8))
  }
}
