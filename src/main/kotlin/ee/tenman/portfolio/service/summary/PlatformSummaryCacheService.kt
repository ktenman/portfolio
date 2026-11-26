package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.PLATFORM_SUMMARY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.configuration.aspect.SingleFlight
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.TimeRange
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate

@Service
class PlatformSummaryCacheService(
  private val summaryService: SummaryService,
  private val cacheManager: CacheManager,
  private val clock: Clock,
) {
  @SingleFlight
  @Cacheable(
    value = [SUMMARY_CACHE],
    key = "'platform-current-' + #root.target.platformKey(#platforms)",
  )
  fun getCurrentDaySummaryForPlatforms(platforms: List<Platform>): PortfolioDailySummary =
    summaryService.getCurrentDaySummaryForPlatforms(platforms)

  @CachePut(
    value = [SUMMARY_CACHE],
    key = "'platform-current-' + #root.target.platformKey(#platforms)",
  )
  fun refreshCurrentDaySummaryForPlatforms(platforms: List<Platform>): PortfolioDailySummary =
    summaryService.getCurrentDaySummaryForPlatforms(platforms)

  @Suppress("UnusedParameter")
  @CachePut(
    value = [SUMMARY_CACHE],
    key = "'platform-current-' + #root.target.platformKey(#platforms)",
  )
  fun putCurrentDaySummaryForPlatforms(
    platforms: List<Platform>,
    summary: PortfolioDailySummary,
  ): PortfolioDailySummary = summary

  @Cacheable(
    value = [PLATFORM_SUMMARY_CACHE],
    key = "'platform-historical-' + #root.target.platformKey(#platforms) + '-' + #page + '-' + #size + '-' + #root.target.today()",
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

  @Cacheable(
    value = [PLATFORM_SUMMARY_CACHE],
    key = "#root.target.seriesKey(#platforms, #range)",
    unless = "#result.isEmpty()",
  )
  fun getSeriesForPlatforms(
    platforms: List<Platform>,
    range: TimeRange,
  ): List<PortfolioDailySummary> = summaryService.getSeriesForPlatforms(platforms, range) { cachedSeries(platforms, it) }

  fun seriesKey(
    platforms: List<Platform>,
    range: TimeRange,
  ): String = "platform-series-${platformKey(platforms)}-${range.name}-${today()}"

  @Suppress("UNCHECKED_CAST")
  private fun cachedSeries(
    platforms: List<Platform>,
    range: TimeRange,
  ): List<PortfolioDailySummary>? =
    cacheManager.getCache(PLATFORM_SUMMARY_CACHE)?.get(seriesKey(platforms, range))?.get() as List<PortfolioDailySummary>?

  fun platformKey(platforms: List<Platform>): String = platforms.map { it.name }.sorted().joinToString(",")

  fun today(): LocalDate = LocalDate.now(clock)
}
