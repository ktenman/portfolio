package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.aspect.Loggable
import ee.tenman.portfolio.service.PortfolioSummaryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate

@RestController
@RequestMapping("/api/portfolio-summary")
class PortfolioSummaryController(
  private val portfolioSummaryService: PortfolioSummaryService
) {

  @GetMapping
  @Loggable
  fun getPortfolioSummary(): List<PortfolioSummaryDto> {
    val historicalSummaries = portfolioSummaryService.getAllDailySummaries()
    val currentDaySummary = portfolioSummaryService.getCurrentDaySummary()

    return (historicalSummaries + currentDaySummary)
      .sortedByDescending { it.entryDate }
      .map { summary ->
        PortfolioSummaryDto(
          date = summary.entryDate,
          totalValue = summary.totalValue,
          xirrAnnualReturn = summary.xirrAnnualReturn,
          totalProfit = summary.totalProfit,
          earningsPerDay = summary.earningsPerDay,
          earningsPerMonth = summary.earningsPerDay.multiply(BigDecimal(365.25/12))
        )
      }
  }

  data class PortfolioSummaryDto(
    val date: LocalDate,
    val totalValue: BigDecimal,
    val xirrAnnualReturn: BigDecimal,
    val totalProfit: BigDecimal,
    val earningsPerDay: BigDecimal,
    val earningsPerMonth: BigDecimal
  )
}
