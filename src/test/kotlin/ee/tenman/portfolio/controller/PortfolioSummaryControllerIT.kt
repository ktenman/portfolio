package ee.tenman.portfolio.controller

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.ninjasquad.springmockk.MockkBean
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.DailyPriceRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import io.mockk.every
import jakarta.annotation.Resource
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

private val DEFAULT_COOKIE = Cookie("AUTHSESSION", "NzEyYmI5ZTMtOTNkNy00MjQyLTgxYmItZWE4ZDA3OWI0N2Uz")

@ExtendWith(OutputCaptureExtension::class)
@IntegrationTest
class PortfolioSummaryControllerIT {
  @Resource
  private lateinit var mockMvc: MockMvc

  @Resource
  private lateinit var portfolioSummaryRepository: PortfolioDailySummaryRepository

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var portfolioTransactionRepository: PortfolioTransactionRepository

  @Resource
  private lateinit var dailyPriceRepository: DailyPriceRepository

  @MockkBean
  lateinit var clock: Clock

  @Test
  fun `should return all portfolio summaries in the correct order when GET request is made to portfolio-summary endpoint`(
    output: CapturedOutput,
  ) {
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
    every { clock.instant() } returns Instant.parse("2023-07-21T10:00:00Z")
    every { clock.zone } returns Clock.systemUTC().zone

    val instrument =
      instrumentRepository.save(
        Instrument(
          symbol = "QDVE",
          name = "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
          category = "ETF",
          baseCurrency = "EUR",
          currentPrice = 28.25.toBigDecimal(),
        ),
      )

    portfolioTransactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = 3.4.toBigDecimal(),
        price = 27.25.toBigDecimal(),
        transactionDate = LocalDate.of(2023, 7, 15),
        platform = Platform.LIGHTYEAR,
      ),
    )

    portfolioSummaryRepository.saveAll(
      listOf(
        PortfolioDailySummary(
          entryDate = LocalDate.of(2023, 7, 19),
          totalValue = BigDecimal("10000.00"),
          xirrAnnualReturn = BigDecimal("0.05"),
          totalProfit = BigDecimal("500.00"),
          earningsPerDay = BigDecimal("15.00"),
        ),
        PortfolioDailySummary(
          entryDate = LocalDate.of(2023, 7, 20),
          totalValue = BigDecimal("10500.00"),
          xirrAnnualReturn = BigDecimal("0.06"),
          totalProfit = BigDecimal("600.00"),
          earningsPerDay = BigDecimal("20.00"),
        ),
        PortfolioDailySummary(
          entryDate = LocalDate.of(2023, 7, 18),
          totalValue = BigDecimal("9500.00"),
          xirrAnnualReturn = BigDecimal("0.04"),
          totalProfit = BigDecimal("400.00"),
          earningsPerDay = BigDecimal("10.00"),
        ),
      ),
    )

    mockMvc
      .perform(
        get("/api/portfolio-summary/historical")
          .param("page", "0")
          .param("size", "3")
          .cookie(DEFAULT_COOKIE),
      ).andExpect(status().isOk)
      .andExpect(jsonPath("$.content").isArray)
      .andExpect(jsonPath("$.content", hasSize<Any>(3)))
      .andExpect(jsonPath("$.content[0].date").value("2023-07-20"))
      .andExpect(jsonPath("$.content[0].totalValue").value(10500.00))
      .andExpect(jsonPath("$.content[0].xirrAnnualReturn").value(0.06))
      .andExpect(jsonPath("$.content[0].totalProfit").value(600.00))
      .andExpect(jsonPath("$.content[0].earningsPerDay").value(20.00))
      .andExpect(jsonPath("$.content[1].date").value("2023-07-19"))
      .andExpect(jsonPath("$.content[1].totalValue").value(10000.00))
      .andExpect(jsonPath("$.content[1].xirrAnnualReturn").value(0.05))
      .andExpect(jsonPath("$.content[1].totalProfit").value(500.00))
      .andExpect(jsonPath("$.content[1].earningsPerDay").value(15.00))
      .andExpect(jsonPath("$.content[2].date").value("2023-07-18"))
      .andExpect(jsonPath("$.content[2].totalValue").value(9500.00))
      .andExpect(jsonPath("$.content[2].xirrAnnualReturn").value(0.04))
      .andExpect(jsonPath("$.content[2].totalProfit").value(400.00))
      .andExpect(jsonPath("$.content[2].earningsPerDay").value(10.00))

    val summaries = portfolioSummaryRepository.findAll()
    expect(summaries).toHaveSize(3)

    val summary1 = summaries.first { it.entryDate == LocalDate.of(2023, 7, 20) }
    val summary2 = summaries.first { it.entryDate == LocalDate.of(2023, 7, 19) }
    val summary3 = summaries.first { it.entryDate == LocalDate.of(2023, 7, 18) }
    expect(summary1.totalValue.compareTo(BigDecimal("10500.00"))).toEqual(0)
    expect(summary2.totalValue.compareTo(BigDecimal("10000.00"))).toEqual(0)
    expect(summary3.totalValue.compareTo(BigDecimal("9500.00"))).toEqual(0)

    expect(output.out).toContain("getHistoricalPortfolioSummary")
    expect(output.out).toContain("entered with arguments: [0,3]")
    expect(output.out).toContain("exited with result:")
    expect(output.out).toContain("\"content\":")
  }

  @Test
  fun `should return current portfolio summary when GET request is made to portfolio-summary current endpoint`(output: CapturedOutput) {
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
    every { clock.instant() } returns Instant.parse("2023-07-21T10:00:00Z")
    every { clock.zone } returns Clock.systemUTC().zone

    val instrument =
      instrumentRepository.save(
        Instrument(
          symbol = "QDVE",
          name = "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
          category = "ETF",
          baseCurrency = "EUR",
          currentPrice = 28.25.toBigDecimal(),
        ),
      )

    dailyPriceRepository.save(
      DailyPrice(
        instrument = instrument,
        entryDate = LocalDate.of(2023, 7, 21),
        providerName = ProviderName.FT,
        openPrice = BigDecimal("28.25"),
        highPrice = BigDecimal("28.25"),
        lowPrice = BigDecimal("28.25"),
        closePrice = BigDecimal("28.25"),
        volume = 1000,
      ),
    )

    portfolioTransactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = 3.4.toBigDecimal(),
        price = 27.25.toBigDecimal(),
        transactionDate = LocalDate.of(2023, 4, 1),
        platform = Platform.LIGHTYEAR,
      ),
    )

    mockMvc
      .perform(get("/api/portfolio-summary/current").cookie(DEFAULT_COOKIE))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.date").value("2023-07-21"))
      .andExpect(jsonPath("$.totalValue").value(96.05))
      .andExpect(jsonPath("$.xirrAnnualReturn").value(closeTo(0.126, 0.01)))
      .andExpect(jsonPath("$.totalProfit").value(3.4))
      .andExpect(jsonPath("$.earningsPerDay").value(closeTo(0.033, 0.01)))
      .andExpect(jsonPath("$.earningsPerMonth").value(closeTo(1.01, 0.1)))
    expect(output.out).toContain("getCurrentPortfolioSummary")
    expect(output.out).toContain("entered with arguments: []")
    expect(output.out).toContain("exited with result:")
    expect(output.out).toContain("\"date\":")
  }
}
