package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.TimeRange
import ee.tenman.portfolio.dto.PortfolioSummaryDto
import ee.tenman.portfolio.service.transaction.TransactionService
import org.springframework.stereotype.Service

@Service
class PortfolioSummarySeriesService(
  private val summaryService: SummaryService,
  private val platformSummaryCacheService: PlatformSummaryCacheService,
  private val transactionService: TransactionService,
) {
  fun getSeries(
    range: TimeRange,
    platforms: List<Platform>?,
  ): List<PortfolioSummaryDto> = getSummaries(range, platforms).map { it.toSummaryDto() }

  fun getSummaries(
    range: TimeRange,
    platforms: List<Platform>?,
  ): List<PortfolioDailySummary> {
    if (platforms == null) return summaryService.getSeries(range)
    if (!transactionService.coversEveryPlatform(platforms)) {
      return platformSummaryCacheService.getSeriesForPlatforms(platforms, range)
    }
    return summaryService.getSeries(range).ifEmpty { platformSummaryCacheService.getSeriesForPlatforms(platforms, range) }
  }
}
