package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.CurrentDaySummaryCacheTestConfiguration
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
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
class CurrentDaySummaryCacheServiceTest {
  @Resource
  private lateinit var currentDaySummaryCacheService: CurrentDaySummaryCacheService

  @Resource
  private lateinit var summaryService: SummaryService

  @Resource
  private lateinit var testCacheManager: CacheManager

  @BeforeEach
  fun setup() {
    clearMocks(summaryService)
    testCacheManager.getCache(SUMMARY_CACHE)?.clear()
  }

  @Test
  fun `should serve cached current day summary without recomputing when the day advances`() {
    every { summaryService.getCurrentDaySummary() } returns summaryOn(LocalDate.of(2024, 3, 11))
    currentDaySummaryCacheService.getCurrentDaySummary()
    every { summaryService.getCurrentDaySummary() } returns summaryOn(LocalDate.of(2024, 3, 12))
    val servedFromCache = currentDaySummaryCacheService.getCurrentDaySummary()
    expect(servedFromCache.entryDate).toEqual(LocalDate.of(2024, 3, 11))
  }

  @Test
  fun `should overwrite cached current day summary with the latest day when refresh is invoked`() {
    every { summaryService.getCurrentDaySummary() } returns summaryOn(LocalDate.of(2024, 3, 11))
    currentDaySummaryCacheService.getCurrentDaySummary()
    every { summaryService.getCurrentDaySummary() } returns summaryOn(LocalDate.of(2024, 3, 12))
    currentDaySummaryCacheService.refreshCurrentDaySummary()
    val servedAfterRefresh = currentDaySummaryCacheService.getCurrentDaySummary()
    expect(servedAfterRefresh.entryDate).toEqual(LocalDate.of(2024, 3, 12))
  }

  @Test
  fun `should keep serving the refreshed day from cache after the clock advances again`() {
    every { summaryService.getCurrentDaySummary() } returns summaryOn(LocalDate.of(2024, 3, 11))
    currentDaySummaryCacheService.getCurrentDaySummary()
    every { summaryService.getCurrentDaySummary() } returns summaryOn(LocalDate.of(2024, 3, 12))
    currentDaySummaryCacheService.refreshCurrentDaySummary()
    every { summaryService.getCurrentDaySummary() } returns summaryOn(LocalDate.of(2024, 3, 13))
    val servedFromCache = currentDaySummaryCacheService.getCurrentDaySummary()
    expect(servedFromCache.entryDate).toEqual(LocalDate.of(2024, 3, 12))
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
