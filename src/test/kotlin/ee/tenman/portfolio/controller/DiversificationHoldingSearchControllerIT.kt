package ee.tenman.portfolio.controller

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import jakarta.annotation.Resource
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@IntegrationTest
class DiversificationHoldingSearchControllerIT {
  @Resource
  private lateinit var mockMvc: MockMvc

  @BeforeEach
  fun setup() {
    stubFor(
      WireMock
        .get(urlPathEqualTo("/user-by-session"))
        .withQueryParam("sessionId", equalTo("holdings-search"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBodyFile("user-details-response.json")),
    )
  }

  @Test
  fun `should default to the first page with a fixed size`() {
    mockMvc
      .perform(search("""{"allocations":[{"instrumentId":1,"percentage":100}]}"""))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.content").isEmpty)
      .andExpect(jsonPath("$.number").value(0))
      .andExpect(jsonPath("$.size").value(100))
      .andExpect(jsonPath("$.totalElements").value(0))
      .andExpect(jsonPath("$.totalPages").value(0))
  }

  @Test
  fun `should reject a negative page`() {
    mockMvc
      .perform(search("""{"allocations":[{"instrumentId":1,"percentage":100}],"page":-1}"""))
      .andExpect(status().isBadRequest)
  }

  @Test
  fun `should reject a query longer than one hundred characters`() {
    mockMvc
      .perform(search("""{"allocations":[{"instrumentId":1,"percentage":100}],"query":"${"x".repeat(101)}"}"""))
      .andExpect(status().isBadRequest)
  }

  @Test
  fun `should allow a query of one hundred characters`() {
    mockMvc
      .perform(search("""{"allocations":[{"instrumentId":1,"percentage":100}],"query":"${"x".repeat(100)}"}"""))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.totalElements").value(0))
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "[]",
      "[{\"instrumentId\":-1,\"percentage\":100}]",
      "[{\"instrumentId\":1,\"percentage\":-1}]",
      "[{\"instrumentId\":1,\"percentage\":0}]",
    ],
  )
  fun `should validate allocations as the calculator does`(allocations: String) {
    mockMvc
      .perform(search("""{"allocations":$allocations}"""))
      .andExpect(status().isBadRequest)
  }

  private fun search(body: String) =
    post("/api/diversification/holdings")
      .contentType(APPLICATION_JSON)
      .cookie(Cookie("AUTHSESSION", "holdings-search"))
      .content(body)
}
