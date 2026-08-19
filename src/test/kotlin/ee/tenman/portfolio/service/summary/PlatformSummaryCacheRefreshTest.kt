package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.CurrentDaySummaryCacheTestConfiguration
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import io.mockk.clearMocks
import io.mockk.every
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.cache.CacheManager
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [CurrentDaySummaryCacheTestConfiguration::class])
@ActiveProfiles("summary-cache-unit-test")
class PlatformSummaryCacheRefreshTest {
  @Resource
  private lateinit var platformSummaryCacheService: PlatformSummaryCacheService

  @Resource
  private lateinit var summaryService: SummaryService

  @Resource
  private lateinit var testCacheManager: CacheManager

  private val platforms = listOf(Platform.TRADING212, Platform.BINANCE)

  @BeforeEach
  fun setup() {
    clearMocks(summaryService)
    testCacheManager.getCache(SUMMARY_CACHE)?.clear()
  }

  @Test
  fun `should serve the refreshed summary from cache so the refresh and the read share a cache key`() {
    every { summaryService.getCurrentDaySummaryForPlatforms(platforms) } returns summaryOn(LocalDate.of(2024, 3, 11))
    platformSummaryCacheService.getCurrentDaySummaryForPlatforms(platforms)
    every { summaryService.getCurrentDaySummaryForPlatforms(platforms) } returns summaryOn(LocalDate.of(2024, 3, 12))
    platformSummaryCacheService.refreshCurrentDaySummaryForPlatforms(platforms)
    val served = platformSummaryCacheService.getCurrentDaySummaryForPlatforms(platforms)
    expect(served.entryDate).toEqual(LocalDate.of(2024, 3, 12))
  }

  @Test
  fun `should populate the cache from a refresh so a later read cannot recompute`() {
    every { summaryService.getCurrentDaySummaryForPlatforms(platforms) } returns summaryOn(LocalDate.of(2024, 3, 11))
    platformSummaryCacheService.refreshCurrentDaySummaryForPlatforms(platforms)
    every { summaryService.getCurrentDaySummaryForPlatforms(platforms) } returns summaryOn(LocalDate.of(2024, 3, 12))
    val served = platformSummaryCacheService.getCurrentDaySummaryForPlatforms(platforms)
    expect(served.entryDate).toEqual(LocalDate.of(2024, 3, 11))
  }

  @Test
  fun `should key the refreshed summary by platform so another platform set cannot read it`() {
    every { summaryService.getCurrentDaySummaryForPlatforms(platforms) } returns summaryOn(LocalDate.of(2024, 3, 11))
    platformSummaryCacheService.refreshCurrentDaySummaryForPlatforms(platforms)
    val other = listOf(Platform.LHV)
    every { summaryService.getCurrentDaySummaryForPlatforms(other) } returns summaryOn(LocalDate.of(2024, 3, 12))
    val served = platformSummaryCacheService.getCurrentDaySummaryForPlatforms(other)
    expect(served.entryDate).toEqual(LocalDate.of(2024, 3, 12))
  }

  private fun summaryOn(date: LocalDate): PortfolioDailySummary =
    PortfolioDailySummary(
      entryDate = date,
      totalValue = BigDecimal.ZERO,
      xirrAnnualReturn = BigDecimal.ZERO,
      totalProfit = BigDecimal.ZERO,
      earningsPerDay = BigDecimal.ZERO,
    )
}
