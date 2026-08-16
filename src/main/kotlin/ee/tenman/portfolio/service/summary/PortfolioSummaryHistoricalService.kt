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
  ): Page<PortfolioSummaryDto>? =
    summaryCacheService
      .getAllDailySummaries(page, size)
      .toDtoPage { summaryCacheService.findByEntryDate(it) }

  private fun forPlatforms(
    platforms: List<Platform>,
    page: Int,
    size: Int,
  ): Page<PortfolioSummaryDto> =
    platformSummaryCacheService
      .getHistoricalSummariesForPlatforms(platforms, page, size)
      .toDtoPage { platformSummaryCacheService.getSummaryForPlatformsOnDate(platforms, it) }
      ?: Page.empty(PageRequest.of(page, size))

  private fun Page<PortfolioDailySummary>.toDtoPage(
    previousDaySummary: (LocalDate) -> PortfolioDailySummary?,
  ): Page<PortfolioSummaryDto>? {
    if (isEmpty) return null
    val previousDay = previousDaySummary(content.minOf { it.entryDate }.minusDays(1))
    val lookup = (content + listOfNotNull(previousDay)).associateBy { it.entryDate }
    return map { it.toDto(lookup) }
  }

  private fun PortfolioDailySummary.toDto(lookup: Map<LocalDate, PortfolioDailySummary>): PortfolioSummaryDto {
    val profitChange24h = lookup[entryDate.minusDays(1)]?.let { totalProfit.subtract(it.totalProfit) }
    return toSummaryDto(profitChange24h)
  }
}
