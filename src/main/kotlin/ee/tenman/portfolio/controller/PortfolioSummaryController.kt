package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.aspect.Loggable
import ee.tenman.portfolio.service.PortfolioSummaryService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate

@RestController
@RequestMapping("/api/portfolio-summary")
class PortfolioSummaryController(
  private val portfolioSummaryService: PortfolioSummaryService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @PostMapping("/recalculate")
  @ResponseStatus(HttpStatus.OK)
  fun recalculatePortfolioSummaries(): Map<String, Any> {
    log.info("Received request to recalculate all portfolio summaries")
    val startTime = System.currentTimeMillis()
    val count = portfolioSummaryService.recalculateAllDailySummaries()
    val duration = System.currentTimeMillis() - startTime

    return mapOf(
      "success" to true,
      "message" to "Successfully recalculated $count daily summaries",
      "count" to count,
      "durationMs" to duration,
    )
  }

  @GetMapping("/historical")
  @Loggable
  fun getHistoricalPortfolioSummary(
    @RequestParam page: Int,
    @RequestParam size: Int,
  ): Page<PortfolioSummaryDto> {
    val historicalSummaries = portfolioSummaryService.getAllDailySummaries(page, size)

    return historicalSummaries.map { summary ->
      PortfolioSummaryDto(
        date = summary.entryDate,
        totalValue = summary.totalValue,
        xirrAnnualReturn = summary.xirrAnnualReturn,
        totalProfit = summary.totalProfit,
        earningsPerDay = summary.earningsPerDay,
        earningsPerMonth = summary.earningsPerDay.multiply(BigDecimal(365.25 / 12)),
      )
    }
  }

  @GetMapping("/current")
  @Loggable
  fun getCurrentPortfolioSummary(): PortfolioSummaryDto {
    val currentDaySummary = portfolioSummaryService.getCurrentDaySummary()

    return PortfolioSummaryDto(
      date = currentDaySummary.entryDate,
      totalValue = currentDaySummary.totalValue,
      xirrAnnualReturn = currentDaySummary.xirrAnnualReturn,
      totalProfit = currentDaySummary.totalProfit,
      earningsPerDay = currentDaySummary.earningsPerDay,
      earningsPerMonth = currentDaySummary.earningsPerDay.multiply(BigDecimal(365.25 / 12)),
    )
  }

  data class PortfolioSummaryDto(
    val date: LocalDate,
    val totalValue: BigDecimal,
    val xirrAnnualReturn: BigDecimal,
    val totalProfit: BigDecimal,
    val earningsPerDay: BigDecimal,
    val earningsPerMonth: BigDecimal,
  )
}
