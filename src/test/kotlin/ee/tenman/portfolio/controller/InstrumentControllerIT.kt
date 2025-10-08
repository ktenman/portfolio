package ee.tenman.portfolio.controller

import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.InstrumentRepository
import jakarta.annotation.Resource
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.cache.CacheManager
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private val DEFAULT_COOKIE = Cookie("AUTHSESSION", "NzEyYmI5ZTMtOTNkNy00MjQyLTgxYmItZWE4ZDA3OWI0N2Uz")

@IntegrationTest
class InstrumentControllerIT {
  @Resource
  private lateinit var mockMvc: MockMvc

  @Resource
  private lateinit var cacheManager: CacheManager

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @BeforeEach
  fun setup() {
    stubFor(
      WireMock
        .get(urlPathEqualTo("/user-by-session"))
        .withQueryParam("sessionId", equalTo("NzEyYmI5ZTMtOTNkNy00MjQyLTgxYmItZWE4ZDA3OWI0N2Uz"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBodyFile("user-details-response.json"),
        ),
    )
    cacheManager.cacheNames.forEach { cacheName -> cacheManager.getCache(cacheName)?.clear() }
  }

  @Test
  fun `should save instrument when POST request is made to instruments endpoint`() {
    mockMvc
      .perform(
        post("/api/instruments")
          .contentType(APPLICATION_JSON)
          .cookie(DEFAULT_COOKIE)
          .content(
            """
            {
              "symbol": "QDVE",
              "name": "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
              "category": "ETF",
              "baseCurrency": "EUR",
              "providerName": "ALPHA_VANTAGE"
            }
            """.trimIndent(),
          ),
      ).andExpect(status().isOk)
      .andExpect(jsonPath("$.symbol").value("QDVE"))
      .andExpect(jsonPath("$.name").value("iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)"))
      .andExpect(jsonPath("$.category").value("ETF"))
      .andExpect(jsonPath("$.baseCurrency").value("EUR"))

    val instruments = instrumentRepository.findAll()
    expect(instruments).toHaveSize(1)
    val instrument = instruments.first()
    expect(instrument.symbol).toEqual("QDVE")
    expect(instrument.name).toEqual("iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)")
    expect(instrument.category).toEqual("ETF")
    expect(instrument.baseCurrency).toEqual("EUR")
  }

  @Test
  fun `should return all instruments when GET request is made to instruments endpoint`() {
    instrumentRepository.saveAll(
      listOf(
        Instrument(
          symbol = "QDVE",
          name = "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
          category = "ETF",
          baseCurrency = "EUR",
        ),
        Instrument(
          symbol = "IUSA",
          name = "iShares Core S&P 500 UCITS ETF USD (Acc)",
          category = "ETF",
          baseCurrency = "USD",
        ),
      ),
    )

    mockMvc
      .perform(get("/api/instruments").cookie(DEFAULT_COOKIE))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$").isArray)
      .andExpect(jsonPath("$[0].symbol").value("QDVE"))
      .andExpect(jsonPath("$[0].name").value("iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)"))
      .andExpect(jsonPath("$[0].category").value("ETF"))
      .andExpect(jsonPath("$[0].baseCurrency").value("EUR"))
      .andExpect(jsonPath("$[1].symbol").value("IUSA"))
      .andExpect(jsonPath("$[1].name").value("iShares Core S&P 500 UCITS ETF USD (Acc)"))
      .andExpect(jsonPath("$[1].category").value("ETF"))
      .andExpect(jsonPath("$[1].baseCurrency").value("USD"))
  }

  @Test
  fun `should update instrument when PUT request is made to instruments endpoint with valid id`() {
    val savedInstrument =
      instrumentRepository.save(
        Instrument(
          symbol = "QDVE",
          name = "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
          category = "ETF",
          baseCurrency = "EUR",
          providerName = ProviderName.ALPHA_VANTAGE,
        ),
      )

    mockMvc
      .perform(
        put("/api/instruments/{id}", savedInstrument.id)
          .cookie(DEFAULT_COOKIE)
          .contentType(APPLICATION_JSON)
          .content(
            """
            {
              "symbol": "QDVE",
              "name": "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
              "category": "ETF",
              "baseCurrency": "USD",
              "providerName": "ALPHA_VANTAGE"
            }
            """.trimIndent(),
          ),
      ).andExpect(status().isOk)
      .andExpect(jsonPath("$.symbol").value("QDVE"))
      .andExpect(jsonPath("$.name").value("iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)"))
      .andExpect(jsonPath("$.category").value("ETF"))
      .andExpect(jsonPath("$.baseCurrency").value("USD"))

    val updatedInstrument = instrumentRepository.findById(savedInstrument.id).get()
    expect(updatedInstrument.name).toEqual("iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)")
    expect(updatedInstrument.baseCurrency).toEqual("USD")
  }

  @Test
  fun `should delete instrument when DELETE request is made to instruments endpoint with valid id`() {
    val savedInstrument =
      instrumentRepository.save(
        Instrument(
          symbol = "QDVE",
          name = "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
          category = "ETF",
          baseCurrency = "EUR",
        ),
      )

    mockMvc
      .perform(delete("/api/instruments/{id}", savedInstrument.id).cookie(DEFAULT_COOKIE))
      .andExpect(status().isNoContent)

    expect(instrumentRepository.findById(savedInstrument.id).isEmpty).toEqual(true)
  }
}
