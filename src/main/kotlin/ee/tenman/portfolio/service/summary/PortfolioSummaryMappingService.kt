package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.dto.PortfolioSummaryDto
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class PortfolioSummaryMappingService(
  private val summaryCacheService: SummaryCacheService,
  private val summaryService: SummaryService,
  private val platformSummaryCacheService: PlatformSummaryCacheService,
) {
  fun getCurrentSummaryDto(platforms: List<Platform>?): PortfolioSummaryDto {
    if (platforms != null) return getFilteredCurrentSummary(platforms)
    val summary = summaryService.getCurrentDaySummary()
    val profitChange24h = summaryCacheService.calculate24hProfitChange(summary)
    return summary.toDto(profitChange24h)
  }

  fun getHistoricalSummariesDto(
    page: Int,
    size: Int,
    platforms: List<Platform>?,
  ): Page<PortfolioSummaryDto> {
    if (platforms != null) return getFilteredHistoricalSummaries(platforms, page, size)
    val summaries = summaryCacheService.getAllDailySummaries(page, size)
    if (summaries.isEmpty) return Page.empty(PageRequest.of(page, size))
    val lookup = buildSummaryLookup(summaries.content)
    return summaries.map { it.toDto(lookup) }
  }

  private fun getFilteredCurrentSummary(platforms: List<Platform>): PortfolioSummaryDto {
    val summary = platformSummaryCacheService.getCurrentDaySummaryForPlatforms(platforms)
    val yesterdaySummary =
      platformSummaryCacheService.getSummaryForPlatformsOnDate(platforms, summary.entryDate.minusDays(1))
    val profitChange24h = summary.totalProfit.subtract(yesterdaySummary.totalProfit)
    return summary.toDto(profitChange24h)
  }

  private fun getFilteredHistoricalSummaries(
    platforms: List<Platform>,
    page: Int,
    size: Int,
  ): Page<PortfolioSummaryDto> {
    val summaries = platformSummaryCacheService.getHistoricalSummariesForPlatforms(platforms, page, size)
    if (summaries.isEmpty) return Page.empty(PageRequest.of(page, size))
    val oldestDate = summaries.content.minOf { it.entryDate }
    val previousDaySummary =
      platformSummaryCacheService.getSummaryForPlatformsOnDate(platforms, oldestDate.minusDays(1))
    val lookup =
      buildMap {
        summaries.content.forEach { put(it.entryDate, it) }
        put(previousDaySummary.entryDate, previousDaySummary)
      }
    return summaries.map { it.toDto(lookup) }
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
