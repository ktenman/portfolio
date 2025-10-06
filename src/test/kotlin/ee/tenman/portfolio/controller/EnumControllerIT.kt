package ee.tenman.portfolio.controller

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.InstrumentCategory
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import jakarta.annotation.Resource
import jakarta.servlet.http.Cookie
import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

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

    val responseBody = result.response.contentAsString
    val enumsMap = objectMapper.readValue(responseBody, object : TypeReference<Map<String, List<String>>>() {})

    expect(enumsMap.keys).toContain("platforms", "providers", "transactionTypes", "categories", "currencies")
    expect(enumsMap["platforms"]).notToEqualNull()
    expect(enumsMap["platforms"]!!).toHaveSize(Platform.entries.size)
    expect(enumsMap["platforms"]!!).toContain(Platform.BINANCE.name, Platform.TRADING212.name)
    expect(enumsMap["providers"]).notToEqualNull()
    expect(enumsMap["providers"]!!).toHaveSize(ProviderName.entries.size)
    expect(enumsMap["providers"]!!).toContain(ProviderName.ALPHA_VANTAGE.name, ProviderName.BINANCE.name)
    expect(enumsMap["transactionTypes"]).notToEqualNull()
    expect(enumsMap["transactionTypes"]!!).toContain.inOrder.only.values(TransactionType.BUY.name, TransactionType.SELL.name)
    expect(enumsMap["categories"]).notToEqualNull()
    expect(enumsMap["categories"]!!).toHaveSize(InstrumentCategory.entries.size)
    expect(enumsMap["categories"]!!).toContain(InstrumentCategory.CRYPTO.name, InstrumentCategory.ETF.name)
    expect(enumsMap["currencies"]).notToEqualNull()
    expect(enumsMap["currencies"]!!).toContain.inOrder.only.values(Currency.EUR.name)
  }
}
