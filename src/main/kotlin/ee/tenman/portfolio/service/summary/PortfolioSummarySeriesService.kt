package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.TimeRange
import ee.tenman.portfolio.dto.PortfolioSummaryDto
import org.springframework.stereotype.Service

@Service
class PortfolioSummarySeriesService(
  private val summaryService: SummaryService,
  private val platformSummaryCacheService: PlatformSummaryCacheService,
) {
  fun getSeries(
    range: TimeRange,
    platforms: List<Platform>?,
  ): List<PortfolioSummaryDto> {
    if (platforms == null) return summaryService.getSeries(range).map { it.toSummaryDto() }
    return platformSummaryCacheService.getSeriesForPlatforms(platforms, range).map { it.toSummaryDto() }
  }
}
