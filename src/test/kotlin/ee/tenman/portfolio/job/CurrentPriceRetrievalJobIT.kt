package ee.tenman.portfolio.job

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.repository.DailyPriceRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import jakarta.annotation.Resource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import java.math.BigDecimal

@IntegrationTest
class CurrentPriceRetrievalJobIT {
  @Resource
  private lateinit var alphaVantageDataRetrievalJob: AlphaVantageDataRetrievalJob

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var dailyPriceRepository: DailyPriceRepository

  @Test
  fun `should not create duplicated rows when triggered multiple times with same data`() {
    assertThat(dailyPriceRepository.findAll()).isEmpty()
    stubFor(
      get(urlPathEqualTo("/query"))
        .withQueryParam("function", equalTo("SYMBOL_SEARCH"))
        .withQueryParam("keywords", matching("QDVE.*"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBodyFile("symbol-search-response.json"),
        ),
    )

    stubFor(
      get(urlPathEqualTo("/query"))
        .withQueryParam("function", equalTo("TIME_SERIES_DAILY"))
        .withQueryParam("symbol", equalTo("QDVE.DEX"))
        .withQueryParam("outputsize", equalTo("full"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBodyFile("alpha-vantage-response.json"),
        ),
    )
    Instrument(
      "QDVE.DEX",
      "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
      "ETF",
      "EUR",
    ).let {
      instrumentRepository.save(it)
    }

    alphaVantageDataRetrievalJob.execute()
    alphaVantageDataRetrievalJob.execute()

    assertThat(dailyPriceRepository.findAll())
      .isNotEmpty
      .hasSize(100)
      .first()
      .satisfies({
        assertThat(it.entryDate).isEqualTo("2024-02-12")
        assertThat(it.instrument.symbol).contains("QDVE")
        assertThat(it.openPrice).isEqualByComparingTo(BigDecimal("25.21"))
        assertThat(it.highPrice).isEqualByComparingTo(BigDecimal("25.335"))
        assertThat(it.lowPrice).isEqualByComparingTo(BigDecimal("25.12500000"))
        assertThat(it.closePrice).isEqualByComparingTo(BigDecimal("25.33000000"))
        assertThat(it.volume).isEqualTo(776199)
      })
  }
}
