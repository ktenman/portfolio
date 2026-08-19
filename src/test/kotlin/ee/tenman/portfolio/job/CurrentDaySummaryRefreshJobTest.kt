package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.service.summary.CurrentDaySummaryCacheService
import ee.tenman.portfolio.service.summary.PlatformSummaryCacheService
import ee.tenman.portfolio.service.transaction.TransactionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class CurrentDaySummaryRefreshJobTest {
  private val currentDayCache = mockk<CurrentDaySummaryCacheService>(relaxed = true)
  private val platformCache = mockk<PlatformSummaryCacheService>(relaxed = true)
  private val transactionService = mockk<TransactionService>()
  private val job = CurrentDaySummaryRefreshJob(currentDayCache, platformCache, transactionService)

  @Test
  fun `should refresh current day summary cache when scheduled refresh runs`() {
    every { transactionService.getDistinctPlatforms() } returns listOf(Platform.LHV)
    job.refresh()
    verify { currentDayCache.refreshCurrentDaySummary() }
  }

  @Test
  fun `should refresh the summary for every known platform so the filtered cache stays warm`() {
    val platforms = listOf(Platform.TRADING212, Platform.BINANCE)
    every { transactionService.getDistinctPlatforms() } returns platforms
    job.refresh()
    verify { platformCache.refreshCurrentDaySummaryForPlatforms(platforms) }
  }

  @Test
  fun `should dont refresh any platform summary when no transactions exist`() {
    every { transactionService.getDistinctPlatforms() } returns emptyList()
    job.refresh()
    verify(exactly = 0) { platformCache.refreshCurrentDaySummaryForPlatforms(any()) }
  }

  @Test
  fun `should not propagate failures when the cache refresh throws`() {
    every { transactionService.getDistinctPlatforms() } returns listOf(Platform.LHV)
    every { currentDayCache.refreshCurrentDaySummary() } throws RuntimeException("price provider unavailable")
    job.refresh()
    verify { currentDayCache.refreshCurrentDaySummary() }
  }

  @Test
  fun `should still refresh the unfiltered summary when resolving platforms throws`() {
    every { transactionService.getDistinctPlatforms() } throws RuntimeException("database unavailable")
    job.refresh()
    verify { currentDayCache.refreshCurrentDaySummary() }
  }
}
