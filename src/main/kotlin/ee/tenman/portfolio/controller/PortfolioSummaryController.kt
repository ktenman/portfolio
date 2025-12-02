package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.aspect.Loggable
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.dto.PortfolioSummaryDto
import ee.tenman.portfolio.service.SummaryCacheService
import ee.tenman.portfolio.service.SummaryService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
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
  private val summaryService: SummaryService,
  private val summaryCacheService: SummaryCacheService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @PostMapping("/recalculate")
  @ResponseStatus(HttpStatus.OK)
  @Loggable
  fun recalculatePortfolioSummaries(): Map<String, Any> {
    log.info("Received request to recalculate all portfolio summaries")
    val startTime = System.currentTimeMillis()
    val count = summaryService.recalculateAllDailySummaries()
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
    val summaries = summaryCacheService.getAllDailySummaries(page, size)
    if (summaries.isEmpty) return Page.empty(PageRequest.of(page, size))
    val lookup = buildSummaryLookup(summaries.content)
    return summaries.map { it.toDto(lookup) }
  }

  @GetMapping("/current")
  @Loggable
  fun getCurrentPortfolioSummary(): PortfolioSummaryDto {
    val summary = summaryService.getCurrentDaySummary()
    val profitChange24h = summaryCacheService.calculate24hProfitChange(summary)
    return summary.toDto(profitChange24h)
  }

  private fun buildSummaryLookup(summaries: List<PortfolioDailySummary>): Map<LocalDate, PortfolioDailySummary> {
    val previousDaySummary = summaryCacheService.findByEntryDate(summaries.minOf { it.entryDate }.minusDays(1))
    return buildMap {
      summaries.forEach { put(it.entryDate, it) }
      previousDaySummary?.let { put(it.entryDate, it) }
    }
  }

  private fun PortfolioDailySummary.toDto(lookup: Map<LocalDate, PortfolioDailySummary>): PortfolioSummaryDto {
    val profitChange24h = lookup[entryDate.minusDays(1)]?.let { totalProfit.subtract(it.totalProfit) }
    return toDto(profitChange24h)
  }

  private fun PortfolioDailySummary.toDto(profitChange24h: BigDecimal?) =
    PortfolioSummaryDto(
    date = entryDate,
    totalValue = totalValue,
    xirrAnnualReturn = xirrAnnualReturn,
    realizedProfit = realizedProfit,
    unrealizedProfit = unrealizedProfit,
    totalProfit = totalProfit,
    earningsPerDay = earningsPerDay,
    earningsPerMonth = earningsPerDay.multiply(DAYS_PER_MONTH),
    totalProfitChange24h = profitChange24h,
  )

  companion object {
    private val DAYS_PER_MONTH = BigDecimal(365.25 / 12)
  }
}
