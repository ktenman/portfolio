package ee.tenman.portfolio.controller

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import jakarta.annotation.Resource
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.whenever
import org.springframework.boot.test.mock.mockito.MockBean
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
import kotlin.text.contains
import kotlin.text.get

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

  @MockBean
  lateinit var clock: Clock

  @Test
  fun `should return all portfolio summaries in the correct order when GET request is made to portfolio-summary endpoint`(
    output: CapturedOutput
  ) {
    stubFor(
      WireMock.get(urlPathEqualTo("/user-by-session"))
        .withQueryParam("sessionId", equalTo("NzEyYmI5ZTMtOTNkNy00MjQyLTgxYmItZWE4ZDA3OWI0N2Uz"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBodyFile("user-details-response.json")
        )
    )
    whenever(clock.instant()).thenReturn(Instant.parse("2023-07-21T10:00:00Z"))
    whenever(clock.zone).thenReturn(Clock.systemUTC().zone)

    val instrument = instrumentRepository.save(
      Instrument(
        symbol = "QDVE",
        name = "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
        category = "ETF",
        baseCurrency = "EUR",
        currentPrice = 28.25.toBigDecimal()
      ),
    )

    portfolioTransactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = 3.4.toBigDecimal(),
        price = 27.25.toBigDecimal(),
        transactionDate = LocalDate.of(2023, 7, 15)
      )
    )

    portfolioSummaryRepository.saveAll(
      listOf(
        PortfolioDailySummary(
          entryDate = LocalDate.of(2023, 7, 19),
          totalValue = BigDecimal("10000.00"),
          xirrAnnualReturn = BigDecimal("0.05"),
          totalProfit = BigDecimal("500.00"),
          earningsPerDay = BigDecimal("15.00")
        ),
        PortfolioDailySummary(
          entryDate = LocalDate.of(2023, 7, 20),
          totalValue = BigDecimal("10500.00"),
          xirrAnnualReturn = BigDecimal("0.06"),
          totalProfit = BigDecimal("600.00"),
          earningsPerDay = BigDecimal("20.00")
        ),
        PortfolioDailySummary(
          entryDate = LocalDate.of(2023, 7, 18),
          totalValue = BigDecimal("9500.00"),
          xirrAnnualReturn = BigDecimal("0.04"),
          totalProfit = BigDecimal("400.00"),
          earningsPerDay = BigDecimal("10.00")
        )
      )
    )

    mockMvc.perform(get("/api/portfolio-summary/historical").cookie(DEFAULT_COOKIE))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$").isArray)
      .andExpect(jsonPath("$", hasSize<Any>(3)))
      .andExpect(jsonPath("$[0].date").value("2023-07-20"))
      .andExpect(jsonPath("$[0].totalValue").value(10500.0000000000))
      .andExpect(jsonPath("$[0].xirrAnnualReturn").value(0.06000000))
      .andExpect(jsonPath("$[0].totalProfit").value(600.0000000000))
      .andExpect(jsonPath("$[0].earningsPerDay").value(20.0000000000))
      .andExpect(jsonPath("$[1].date").value("2023-07-19"))
      .andExpect(jsonPath("$[1].totalValue").value(10000.0000000000))
      .andExpect(jsonPath("$[1].xirrAnnualReturn").value(0.05000000))
      .andExpect(jsonPath("$[1].totalProfit").value(500.0000000000))
      .andExpect(jsonPath("$[1].earningsPerDay").value(15.0000000000))
      .andExpect(jsonPath("$[2].date").value("2023-07-18"))
      .andExpect(jsonPath("$[2].totalValue").value(9500.0000000000))
      .andExpect(jsonPath("$[2].xirrAnnualReturn").value(0.04000000))
      .andExpect(jsonPath("$[2].totalProfit").value(400.0000000000))
      .andExpect(jsonPath("$[2].earningsPerDay").value(10.0000000000))

    val summaries = portfolioSummaryRepository.findAll()
    assertThat(summaries).hasSize(3)

    val summary1 = summaries.first { it.entryDate == LocalDate.of(2023, 7, 20) }
    val summary2 = summaries.first { it.entryDate == LocalDate.of(2023, 7, 19) }
    val summary3 = summaries.first { it.entryDate == LocalDate.of(2023, 7, 18) }
    assertThat(summary1.totalValue).isEqualByComparingTo("10500.00")
    assertThat(summary2.totalValue).isEqualByComparingTo("10000.00")
    assertThat(summary3.totalValue).isEqualByComparingTo("9500.00")

    assertThat(output.out)
      .contains("PortfolioSummaryController.getHistoricalPortfolioSummary() entered with arguments: []")
      .containsIgnoringCase("PortfolioSummaryController.getHistoricalPortfolioSummary() exited with result: [{\"date\":\"")
  }

  @Test
  fun `should return current portfolio summary when GET request is made to portfolio-summary current endpoint`(
    output: CapturedOutput
  ) {
    stubFor(
      WireMock.get(urlPathEqualTo("/user-by-session"))
        .withQueryParam("sessionId", equalTo("NzEyYmI5ZTMtOTNkNy00MjQyLTgxYmItZWE4ZDA3OWI0N2Uz"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBodyFile("user-details-response.json")
        )
    )
    whenever(clock.instant()).thenReturn(Instant.parse("2023-07-21T10:00:00Z"))
    whenever(clock.zone).thenReturn(Clock.systemUTC().zone)

    val instrument = instrumentRepository.save(
      Instrument(
        symbol = "QDVE",
        name = "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
        category = "ETF",
        baseCurrency = "EUR",
        currentPrice = 28.25.toBigDecimal()
      ),
    )

    portfolioTransactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = 3.4.toBigDecimal(),
        price = 27.25.toBigDecimal(),
        transactionDate = LocalDate.of(2023, 7, 15)
      )
    )

    val currentSummary = portfolioSummaryRepository.save(
      PortfolioDailySummary(
        entryDate = LocalDate.of(2023, 7, 21),
        totalValue = BigDecimal("11000.00"),
        xirrAnnualReturn = BigDecimal("0.07"),
        totalProfit = BigDecimal("700.00"),
        earningsPerDay = BigDecimal("25.00")
      )
    )

    mockMvc.perform(get("/api/portfolio-summary/current").cookie(DEFAULT_COOKIE))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.date").value("2023-07-21"))
      .andExpect(jsonPath("$.totalValue").value(96.05))
      .andExpect(jsonPath("$.xirrAnnualReturn").value(7.97040776))
      .andExpect(jsonPath("$.totalProfit").value(3.4))
      .andExpect(jsonPath("$.earningsPerDay").value(2.096))
      .andExpect(jsonPath("$.earningsPerMonth").value(63.797))

    val summary = portfolioSummaryRepository.findById(currentSummary.id)
    assertThat(summary).isPresent
    assertThat(summary.get().totalValue).isEqualByComparingTo("11000.00")

    assertThat(output.out)
      .contains("PortfolioSummaryController.getCurrentPortfolioSummary() entered with arguments: []")
      .containsIgnoringCase("PortfolioSummaryController.getCurrentPortfolioSummary() exited with result: {\"date\":\"")
  }
}
