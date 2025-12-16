package ee.tenman.portfolio.controller

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import jakarta.annotation.Resource
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get as mvcGet

private val AUTH_COOKIE = Cookie("AUTHSESSION", "NzEyYmI5ZTMtOTNkNy00MjQyLTgxYmItZWE4ZDA3OWI0N2Uz")

@IntegrationTest
class Auto24ControllerIT {
  @Resource
  private lateinit var mockMvc: MockMvc

  @BeforeEach
  fun setup() {
    stubFor(
      get(urlPathEqualTo("/user-by-session"))
        .withQueryParam("sessionId", equalTo("NzEyYmI5ZTMtOTNkNy00MjQyLTgxYmItZWE4ZDA3OWI0N2Uz"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBodyFile("user-details-response.json"),
        ),
    )
  }

  @Test
  fun `should return price range when valid plate number is provided`() {
    stubFor(
      get(urlPathEqualTo("/auto24/price"))
        .withQueryParam("regNumber", equalTo("876BCH"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBody(
              """
              {
                "registrationNumber": "876BCH",
                "marketPrice": "3400 € kuni 8300 €",
                "error": null,
                "attempts": 1,
                "durationSeconds": 1.5
              }
              """.trimIndent(),
            ),
        ),
    )

    mockMvc
      .perform(mvcGet("/api/auto24/price").param("regNr", "876BCH").cookie(AUTH_COOKIE))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.regNr").value("876BCH"))
      .andExpect(jsonPath("$.price").value("3400 € kuni 8300 €"))
      .andExpect(jsonPath("$.durationSeconds").value(1.5))
  }

  @Test
  fun `should return vehicle not found when plate does not exist`() {
    stubFor(
      get(urlPathEqualTo("/auto24/price"))
        .withQueryParam("regNumber", equalTo("XXXXXX"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBody(
              """
              {
                "registrationNumber": "XXXXXX",
                "marketPrice": null,
                "error": "Vehicle not found",
                "attempts": 1,
                "durationSeconds": 0.5
              }
              """.trimIndent(),
            ),
        ),
    )

    mockMvc
      .perform(mvcGet("/api/auto24/price").param("regNr", "XXXXXX").cookie(AUTH_COOKIE))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.regNr").value("XXXXXX"))
      .andExpect(jsonPath("$.price").value("Vehicle not found"))
      .andExpect(jsonPath("$.durationSeconds").value(0.5))
  }

  @Test
  fun `should return price not available when no price data exists`() {
    stubFor(
      get(urlPathEqualTo("/auto24/price"))
        .withQueryParam("regNumber", equalTo("123ABC"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBody(
              """
              {
                "registrationNumber": "123ABC",
                "marketPrice": null,
                "error": "Price not available",
                "attempts": 1,
                "durationSeconds": 0.8
              }
              """.trimIndent(),
            ),
        ),
    )

    mockMvc
      .perform(mvcGet("/api/auto24/price").param("regNr", "123ABC").cookie(AUTH_COOKIE))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.regNr").value("123ABC"))
      .andExpect(jsonPath("$.price").value("Price not available"))
      .andExpect(jsonPath("$.durationSeconds").value(0.8))
  }
}
