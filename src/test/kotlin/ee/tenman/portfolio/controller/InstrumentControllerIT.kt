package ee.tenman.portfolio.controller

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
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
import net.datafaker.Faker
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
  private val faker = Faker()

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

  private fun randomInstrument() =
    Instrument(
      symbol = faker.stock().nsdqSymbol(),
      name = faker.company().name(),
      category = listOf("Stock", "ETF", "Crypto").random(),
      baseCurrency = listOf("USD", "EUR", "GBP").random(),
      providerName = ProviderName.FT,
    )

  @Test
  fun `should save instrument when POST request is made to instruments endpoint`() {
    val testInstrument = randomInstrument()

    mockMvc
      .perform(
        post("/api/instruments")
          .contentType(APPLICATION_JSON)
          .cookie(DEFAULT_COOKIE)
          .content(
            """
            {
              "symbol": "${testInstrument.symbol}",
              "name": "${testInstrument.name}",
              "category": "${testInstrument.category}",
              "baseCurrency": "${testInstrument.baseCurrency}",
              "providerName": "${testInstrument.providerName}"
            }
            """.trimIndent(),
          ),
      ).andExpect(status().isOk)
      .andExpect(jsonPath("$.symbol").value(testInstrument.symbol))
      .andExpect(jsonPath("$.name").value(testInstrument.name))
      .andExpect(jsonPath("$.category").value(testInstrument.category))
      .andExpect(jsonPath("$.baseCurrency").value(testInstrument.baseCurrency))

    val instruments = instrumentRepository.findAll()
    expect(instruments).toHaveSize(1)
    val instrument = instruments.first()
    expect(instrument.symbol).toEqual(testInstrument.symbol)
    expect(instrument.name).toEqual(testInstrument.name)
    expect(instrument.category).toEqual(testInstrument.category)
    expect(instrument.baseCurrency).toEqual(testInstrument.baseCurrency)
  }

  @Test
  fun `should return all instruments when GET request is made to instruments endpoint`() {
    val instrument1 = randomInstrument()
    val instrument2 = randomInstrument()

    instrumentRepository.saveAll(listOf(instrument1, instrument2))

    mockMvc
      .perform(get("/api/instruments").cookie(DEFAULT_COOKIE))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.instruments").isArray)
      .andExpect(jsonPath("$.portfolioXirr").value(org.hamcrest.Matchers.nullValue()))
      .andExpect(jsonPath("$.instruments[0].symbol").value(instrument1.symbol))
      .andExpect(jsonPath("$.instruments[0].name").value(instrument1.name))
      .andExpect(jsonPath("$.instruments[0].category").value(instrument1.category))
      .andExpect(jsonPath("$.instruments[0].baseCurrency").value(instrument1.baseCurrency))
      .andExpect(jsonPath("$.instruments[1].symbol").value(instrument2.symbol))
      .andExpect(jsonPath("$.instruments[1].name").value(instrument2.name))
      .andExpect(jsonPath("$.instruments[1].category").value(instrument2.category))
      .andExpect(jsonPath("$.instruments[1].baseCurrency").value(instrument2.baseCurrency))
  }

  @Test
  fun `should update instrument when PUT request is made to instruments endpoint with valid id`() {
    val originalInstrument = randomInstrument()
    val savedInstrument = instrumentRepository.save(originalInstrument)

    val updatedBaseCurrency = listOf("USD", "EUR", "GBP").filter { it != originalInstrument.baseCurrency }.random()

    mockMvc
      .perform(
        put("/api/instruments/{id}", savedInstrument.id)
          .cookie(DEFAULT_COOKIE)
          .contentType(APPLICATION_JSON)
          .content(
            """
            {
              "symbol": "${originalInstrument.symbol}",
              "name": "${originalInstrument.name}",
              "category": "${originalInstrument.category}",
              "baseCurrency": "$updatedBaseCurrency",
              "providerName": "${originalInstrument.providerName}"
            }
            """.trimIndent(),
          ),
      ).andExpect(status().isOk)
      .andExpect(jsonPath("$.symbol").value(originalInstrument.symbol))
      .andExpect(jsonPath("$.name").value(originalInstrument.name))
      .andExpect(jsonPath("$.category").value(originalInstrument.category))
      .andExpect(jsonPath("$.baseCurrency").value(updatedBaseCurrency))

    val updatedInstrument = instrumentRepository.findById(savedInstrument.id).get()
    expect(updatedInstrument.name).toEqual(originalInstrument.name)
    expect(updatedInstrument.baseCurrency).toEqual(updatedBaseCurrency)
  }

  @Test
  fun `should delete instrument when DELETE request is made to instruments endpoint with valid id`() {
    val testInstrument = randomInstrument()
    val savedInstrument = instrumentRepository.save(testInstrument)

    mockMvc
      .perform(delete("/api/instruments/{id}", savedInstrument.id).cookie(DEFAULT_COOKIE))
      .andExpect(status().isNoContent)

    expect(instrumentRepository.findById(savedInstrument.id).isEmpty).toEqual(true)
  }
}
