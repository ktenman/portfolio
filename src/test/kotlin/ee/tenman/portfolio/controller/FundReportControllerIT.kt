package ee.tenman.portfolio.controller

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.service.infrastructure.MinioService
import jakarta.annotation.Resource
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.APPLICATION_PDF
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get as mvcGet

@IntegrationTest
class FundReportControllerIT {
  @Resource
  private lateinit var mockMvc: MockMvc

  @Resource
  private lateinit var minioService: MinioService

  @BeforeEach
  fun setup() {
    stubFor(
      get(urlPathEqualTo("/user-by-session"))
        .withQueryParam("sessionId", equalTo(SESSION))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBodyFile("user-details-response.json"),
        ),
    )
  }

  @Test
  fun `should serve an archived fund report as a PDF`() {
    val isin = "EE${UUID.randomUUID().toString().filter(Char::isLetterOrDigit).take(10).uppercase()}"
    val pdf = "%PDF-1.7 Tuleva aruanne õ".toByteArray()
    minioService.uploadFundReport(isin, LocalDate.of(2026, 8, 31), pdf)
    mockMvc
      .perform(mvcGet("/api/funds/$isin/reports/2026-08-31").cookie(Cookie("AUTHSESSION", SESSION)))
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_PDF))
      .andExpect(content().bytes(pdf))
  }

  @Test
  fun `should return not found for a report that was never archived`() {
    mockMvc
      .perform(mvcGet("/api/funds/EE3600001707/reports/1999-01-31").cookie(Cookie("AUTHSESSION", SESSION)))
      .andExpect(status().isNotFound)
  }

  companion object {
    private const val SESSION = "NzEyYmI5ZTMtOTNkNy00MjQyLTgxYmItZWE4ZDA3OWI0N2Uz"
  }
}
