package ee.tenman.portfolio.service.xirr

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.IntegrationTest
import jakarta.annotation.Resource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE

@IntegrationTest
@AutoConfigureWireMock(port = 0)
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

    assertThat(calculateStockXirr).isEqualTo(1.2677981466313628)
  }
}
