package ee.tenman.portfolio.controller

import ch.tutteli.atrium.api.fluent.en_GB.inOrder
import ch.tutteli.atrium.api.fluent.en_GB.only
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.fluent.en_GB.values
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.EnumsResponse
import ee.tenman.portfolio.domain.InstrumentCategory
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import jakarta.annotation.Resource
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

private val DEFAULT_COOKIE = Cookie("AUTHSESSION", "NzEyYmI5ZTMtOTNkNy00MjQyLTgxYmItZWE4ZDA3OWI0N2Uz")

@IntegrationTest
class EnumControllerIT {
  @Resource
  private lateinit var mockMvc: MockMvc

  @Resource
  private lateinit var objectMapper: ObjectMapper

  @BeforeEach
  fun setup() {
    stubFor(
      WireMock
        .get(urlPathEqualTo("/user-by-session"))
        .withQueryParam("sessionId", equalTo("NzEyYmI5ZTMtOTNkNy00MjQyLTgxYmItZWE4ZDA3OWI0N2Uz"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBody(
              """
              {
                  "id": "3f71e9f4-fce0-441e-94fe-f1298e4daba4",
                  "email": "test@example.com",
                  "provider": "GOOGLE"
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  @Test
  fun `should return all enums in one call`() {
    val result =
      mockMvc
        .perform(get("/api/enums").cookie(DEFAULT_COOKIE))
        .andExpect(status().isOk)
        .andReturn()

    val response = objectMapper.readValue(result.response.contentAsString, EnumsResponse::class.java)

    expect(response.platforms)
      .toHaveSize(Platform.entries.size)
      .toContain(Platform.BINANCE.name, Platform.TRADING212.name)

    expect(response.providers)
      .toHaveSize(ProviderName.entries.size)
      .toContain(ProviderName.ALPHA_VANTAGE.name, ProviderName.BINANCE.name)

    expect(response.transactionTypes)
      .toContain.inOrder.only
      .values(TransactionType.BUY.name, TransactionType.SELL.name)

    expect(response.categories)
      .toHaveSize(InstrumentCategory.entries.size)
      .toContain(InstrumentCategory.CRYPTO.name, InstrumentCategory.ETF.name)

    expect(response.currencies)
      .toContain.inOrder.only
      .values(Currency.EUR.name)
  }
}
