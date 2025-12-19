package ee.tenman.portfolio.controller

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import jakarta.annotation.Resource
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get as mvcGet

private val AUTH_COOKIE = Cookie("AUTHSESSION", "test-session-id")

@IntegrationTest
@TestPropertySource(properties = ["veego.url=http://localhost:\${wiremock.server.port}"])
class VehicleInfoControllerIT {
  @Resource
  private lateinit var mockMvc: MockMvc

  @BeforeEach
  fun setup() {
    stubFor(
      get(urlPathEqualTo("/user-by-session"))
        .withQueryParam("sessionId", equalTo("test-session-id"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBodyFile("user-details-response.json"),
        ),
    )
  }

  @Test
  fun `should return combined vehicle info when both APIs succeed`() {
    stubAuto24Success("876BCH", "3400 € kuni 8300 €")
    stubVeegoSuccess("876BCH")

    mockMvc
      .perform(mvcGet("/api/vehicle/info").param("plateNumber", "876BCH").cookie(AUTH_COOKIE))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.plateNumber").value("876BCH"))
      .andExpect(jsonPath("$.marketPrice").value("3400 € to 8300 €"))
      .andExpect(jsonPath("$.annualTax").value(94.14))
      .andExpect(jsonPath("$.registrationTax").value(599.5))
      .andExpect(jsonPath("$.make").value("Subaru"))
      .andExpect(jsonPath("$.model").value("Forester"))
      .andExpect(jsonPath("$.year").value(2009))
      .andExpect(jsonPath("$.group").value("Passenger car"))
      .andExpect(jsonPath("$.co2").value(199))
      .andExpect(jsonPath("$.fuel").value("Petrol"))
      .andExpect(jsonPath("$.weight").value(2015))
      .andExpect(jsonPath("$.auto24Error").doesNotExist())
      .andExpect(jsonPath("$.veegoError").doesNotExist())
      .andExpect(jsonPath("$.formattedText").exists())
  }

  @Test
  fun `should return partial data when auto24 fails but veego succeeds`() {
    stubAuto24VehicleNotFound("999ZZZ")
    stubVeegoSuccess("999ZZZ")

    mockMvc
      .perform(mvcGet("/api/vehicle/info").param("plateNumber", "999ZZZ").cookie(AUTH_COOKIE))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.plateNumber").value("999ZZZ"))
      .andExpect(jsonPath("$.marketPrice").doesNotExist())
      .andExpect(jsonPath("$.auto24Error").value("Vehicle not found"))
      .andExpect(jsonPath("$.annualTax").value(94.14))
      .andExpect(jsonPath("$.veegoError").doesNotExist())
      .andExpect(jsonPath("$.formattedText").value(not(containsString("Market Price"))))
  }

  @Test
  fun `should return partial data when veego fails but auto24 succeeds`() {
    stubAuto24Success("876BCH", "5000 €")
    stubVeegoError("876BCH")

    mockMvc
      .perform(mvcGet("/api/vehicle/info").param("plateNumber", "876BCH").cookie(AUTH_COOKIE))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.plateNumber").value("876BCH"))
      .andExpect(jsonPath("$.marketPrice").value("5000 €"))
      .andExpect(jsonPath("$.auto24Error").doesNotExist())
      .andExpect(jsonPath("$.annualTax").doesNotExist())
      .andExpect(jsonPath("$.veegoError").exists())
  }

  @Test
  fun `should include formatted text with emojis in response`() {
    stubAuto24Success("876BCH", "3400 € kuni 8300 €")
    stubVeegoSuccess("876BCH")

    mockMvc
      .perform(mvcGet("/api/vehicle/info").param("plateNumber", "876BCH").cookie(AUTH_COOKIE))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.formattedText").value(containsString("🚗 Subaru Forester")))
      .andExpect(jsonPath("$.formattedText").value(containsString("📋 Details:")))
      .andExpect(jsonPath("$.formattedText").value(containsString("💰 Tax Information:")))
      .andExpect(jsonPath("$.formattedText").value(containsString("💵 Market Price:")))
  }

  private fun stubAuto24Success(
    plateNumber: String,
    price: String,
  ) {
    stubFor(
      get(urlPathEqualTo("/auto24/price"))
        .withQueryParam("regNumber", equalTo(plateNumber))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBody(
              """
              {
                "registrationNumber": "$plateNumber",
                "marketPrice": "$price",
                "error": null,
                "attempts": 1,
                "durationSeconds": 1.5
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  private fun stubAuto24VehicleNotFound(plateNumber: String) {
    stubFor(
      get(urlPathEqualTo("/auto24/price"))
        .withQueryParam("regNumber", equalTo(plateNumber))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBody(
              """
              {
                "registrationNumber": "$plateNumber",
                "marketPrice": null,
                "error": "Vehicle not found",
                "attempts": 1,
                "durationSeconds": 0.5
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  private fun stubVeegoSuccess(plateNumber: String) {
    stubFor(
      post(urlPathEqualTo("/vehicles/$plateNumber/tax"))
        .withRequestBody(equalToJson("""{"reg":"$plateNumber"}"""))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBody(
              """
              {
                "annual_tax": 94.14,
                "registration_tax": 599.5,
                "make": "Subaru",
                "model": "Forester",
                "year": 2009,
                "group": "Passenger car",
                "co2": 199,
                "fuel": "Petrol",
                "weight": 2015
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  private fun stubVeegoError(plateNumber: String) {
    stubFor(
      post(urlPathEqualTo("/vehicles/$plateNumber/tax"))
        .willReturn(
          aResponse()
            .withStatus(500)
            .withBody("Internal Server Error"),
        ),
    )
  }
}
