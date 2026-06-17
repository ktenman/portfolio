package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.summary.CurrentDaySummaryCacheService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class CurrentDaySummaryRefreshJobTest {
  @Test
  fun `should refresh current day summary cache when scheduled refresh runs`() {
    val cacheService = mockk<CurrentDaySummaryCacheService>()
    every { cacheService.refreshCurrentDaySummary() } returns mockk()
    CurrentDaySummaryRefreshJob(cacheService).refresh()
    verify { cacheService.refreshCurrentDaySummary() }
  }

  @Test
  fun `should not propagate failures when the cache refresh throws`() {
    val cacheService = mockk<CurrentDaySummaryCacheService>()
    every { cacheService.refreshCurrentDaySummary() } throws RuntimeException("price provider unavailable")
    CurrentDaySummaryRefreshJob(cacheService).refresh()
    verify { cacheService.refreshCurrentDaySummary() }
  }
}
