package ee.tenman.portfolio.controller

import ee.tenman.portfolio.IntegrationTest
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import jakarta.annotation.Resource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(OutputCaptureExtension::class)
@IntegrationTest
class PortfolioSummaryControllerIT {

  @Resource
  private lateinit var mockMvc: MockMvc

  @Resource
  private lateinit var portfolioSummaryRepository: PortfolioDailySummaryRepository

  @Test
  fun `should return all portfolio summaries in the correct order when GET request is made to portfolio-summary endpoint`(
    output: CapturedOutput
  ) {
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

    mockMvc.perform(get("/api/portfolio-summary"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$").isArray)
      .andExpect(jsonPath("$[0].date").value("2023-07-20"))
      .andExpect(jsonPath("$[0].totalValue").value(10500.00))
      .andExpect(jsonPath("$[0].xirrAnnualReturn").value(0.06))
      .andExpect(jsonPath("$[0].totalProfit").value(600.00))
      .andExpect(jsonPath("$[0].earningsPerDay").value(20.00))
      .andExpect(jsonPath("$[1].date").value("2023-07-19"))
      .andExpect(jsonPath("$[1].totalValue").value(10000.00))
      .andExpect(jsonPath("$[1].xirrAnnualReturn").value(0.05))
      .andExpect(jsonPath("$[1].totalProfit").value(500.00))
      .andExpect(jsonPath("$[1].earningsPerDay").value(15.00))
      .andExpect(jsonPath("$[2].date").value("2023-07-18"))
      .andExpect(jsonPath("$[2].totalValue").value(9500.00))
      .andExpect(jsonPath("$[2].xirrAnnualReturn").value(0.04))
      .andExpect(jsonPath("$[2].totalProfit").value(400.00))
      .andExpect(jsonPath("$[2].earningsPerDay").value(10.00))


    val summaries = portfolioSummaryRepository.findAll()
    assertThat(summaries).hasSize(3)

    val summary1 = summaries.first { it.entryDate == LocalDate.of(2023, 7, 20) }
    val summary2 = summaries.first { it.entryDate == LocalDate.of(2023, 7, 19) }
    val summary3 = summaries.first { it.entryDate == LocalDate.of(2023, 7, 18) }
    assertThat(summary1.totalValue).isEqualByComparingTo("10500.00")
    assertThat(summary2.totalValue).isEqualByComparingTo("10000.00")
    assertThat(summary3.totalValue).isEqualByComparingTo("9500.00")

    assertThat(output.out)
      .contains("PortfolioSummaryController.getPortfolioSummary() entered with arguments: []")
      .containsIgnoringCase("PortfolioSummaryController.getPortfolioSummary() exited with result: [{\"date\":\"")
  }
}
