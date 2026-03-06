package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.PLATFORM_SUMMARY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PlatformSummaryCacheService(
  private val summaryService: SummaryService,
) {
  @Cacheable(
    value = [SUMMARY_CACHE],
    key = "'platform-current-' + #root.target.platformKey(#platforms)",
  )
  fun getCurrentDaySummaryForPlatforms(platforms: List<Platform>): PortfolioDailySummary =
    summaryService.getCurrentDaySummaryForPlatforms(platforms)

  @Cacheable(
    value = [PLATFORM_SUMMARY_CACHE],
    key = "'platform-historical-' + #root.target.platformKey(#platforms) + '-' + #page + '-' + #size",
    unless = "#result.isEmpty()",
  )
  fun getHistoricalSummariesForPlatforms(
    platforms: List<Platform>,
    page: Int,
    size: Int,
  ): Page<PortfolioDailySummary> = summaryService.getHistoricalSummariesForPlatforms(platforms, page, size)

  @Cacheable(
    value = [PLATFORM_SUMMARY_CACHE],
    key = "'platform-date-' + #root.target.platformKey(#platforms) + '-' + #date.toString()",
  )
  fun getSummaryForPlatformsOnDate(
    platforms: List<Platform>,
    date: LocalDate,
  ): PortfolioDailySummary = summaryService.getSummaryForPlatformsOnDate(platforms, date)

  fun platformKey(platforms: List<Platform>): String = platforms.map { it.name }.sorted().joinToString(",")
}
