package ee.tenman.portfolio.controller

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.FundAllocation
import ee.tenman.portfolio.repository.FundAllocationRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get as mvcGet

@IntegrationTest
class FundReportControllerIT {
  @Resource
  private lateinit var mockMvc: MockMvc

  @Resource
  private lateinit var minioService: MinioService

  @Resource
  private lateinit var fundAllocationRepository: FundAllocationRepository

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

  @Test
  fun `should list the fund allocation of each report`() {
    val isin = "EE${UUID.randomUUID().toString().filter(Char::isLetterOrDigit).take(10).uppercase()}"
    fundAllocationRepository.save(
      FundAllocation(
        fundIsin = isin,
        asOfDate = LocalDate.of(2026, 8, 31),
        sourceUrl = "https://tuleva.ee/$isin.pdf",
        reportedTotal = BigDecimal("100.02"),
        underlyingIsin = "IE00BKPTWY98",
        underlyingName = "iShares Emerging Market Screened Equity Index Fund õ",
        weight = BigDecimal("12.55"),
      ),
    )
    mockMvc
      .perform(mvcGet("/api/funds/$isin/reports").cookie(Cookie("AUTHSESSION", SESSION)))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$[0].asOfDate").value("2026-08-31"))
      .andExpect(jsonPath("$[0].funds[0].isin").value("IE00BKPTWY98"))
  }

  companion object {
    private const val SESSION = "NzEyYmI5ZTMtOTNkNy00MjQyLTgxYmItZWE4ZDA3OWI0N2Uz"
  }
}
