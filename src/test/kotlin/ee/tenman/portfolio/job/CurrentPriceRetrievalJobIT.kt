package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
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
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal

@IntegrationTest
@TestPropertySource(properties = ["scheduling.enabled=true"])
class CurrentPriceRetrievalJobIT {
  @Resource
  private lateinit var alphaVantageDataRetrievalJob: AlphaVantageDataRetrievalJob

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var dailyPriceRepository: DailyPriceRepository

  @Test
  fun `should not create duplicated rows when triggered multiple times with same data`() {
    expect(dailyPriceRepository.findAll()).toBeEmpty()
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

    val dailyPrices = dailyPriceRepository.findAll()
    expect(dailyPrices.isEmpty()).toEqual(false)
    expect(dailyPrices.size).toEqual(100)
    val firstPrice = dailyPrices.first()
    expect(firstPrice.entryDate.toString()).toEqual("2024-02-12")
    expect(firstPrice.instrument.symbol).toContain("QDVE")
    expect(firstPrice.openPrice?.compareTo(BigDecimal("25.21"))).toEqual(0)
    expect(firstPrice.highPrice?.compareTo(BigDecimal("25.335"))).toEqual(0)
    expect(firstPrice.lowPrice?.compareTo(BigDecimal("25.12500000"))).toEqual(0)
    expect(firstPrice.closePrice.compareTo(BigDecimal("25.33000000"))).toEqual(0)
    expect(firstPrice.volume).toEqual(776199)
  }
}
