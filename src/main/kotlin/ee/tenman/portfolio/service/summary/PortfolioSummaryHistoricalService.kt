package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.dto.PortfolioSummaryDto
import ee.tenman.portfolio.service.transaction.TransactionService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PortfolioSummaryHistoricalService(
  private val summaryCacheService: SummaryCacheService,
  private val platformSummaryCacheService: PlatformSummaryCacheService,
  private val transactionService: TransactionService,
) {
  fun getHistorical(
    page: Int,
    size: Int,
    platforms: List<Platform>?,
  ): Page<PortfolioSummaryDto> {
    if (platforms == null) return storedPage(page, size) ?: Page.empty(PageRequest.of(page, size))
    if (!transactionService.coversEveryPlatform(platforms)) return forPlatforms(platforms, page, size)
    return storedPage(page, size) ?: forPlatforms(platforms, page, size)
  }

  private fun storedPage(
    page: Int,
    size: Int,
  ): Page<PortfolioSummaryDto>? {
    val summaries = summaryCacheService.getAllDailySummaries(page, size)
    if (summaries.isEmpty) return null
    val lookup = storedLookup(summaries.content)
    return summaries.map { it.toDto(lookup) }
  }

  private fun forPlatforms(
    platforms: List<Platform>,
    page: Int,
    size: Int,
  ): Page<PortfolioSummaryDto> {
    val summaries = platformSummaryCacheService.getHistoricalSummariesForPlatforms(platforms, page, size)
    if (summaries.isEmpty) return Page.empty(PageRequest.of(page, size))
    val oldestDate = summaries.content.minOf { it.entryDate }
    val previousDaySummary = platformSummaryCacheService.getSummaryForPlatformsOnDate(platforms, oldestDate.minusDays(1))
    val lookup =
      buildMap {
        summaries.content.forEach { put(it.entryDate, it) }
        put(previousDaySummary.entryDate, previousDaySummary)
      }
    return summaries.map { it.toDto(lookup) }
  }

  private fun storedLookup(summaries: List<PortfolioDailySummary>): Map<LocalDate, PortfolioDailySummary> {
    val previousDaySummary = summaryCacheService.findByEntryDate(summaries.minOf { it.entryDate }.minusDays(1))
    return buildMap {
      summaries.forEach { put(it.entryDate, it) }
      previousDaySummary?.let { put(it.entryDate, it) }
    }
  }

  private fun PortfolioDailySummary.toDto(lookup: Map<LocalDate, PortfolioDailySummary>): PortfolioSummaryDto {
    val profitChange24h = lookup[entryDate.minusDays(1)]?.let { totalProfit.subtract(it.totalProfit) }
    return toSummaryDto(profitChange24h)
  }
}
