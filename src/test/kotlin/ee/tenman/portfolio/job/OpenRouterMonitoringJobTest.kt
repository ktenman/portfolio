package ee.tenman.portfolio.job

import ee.tenman.portfolio.openrouter.OpenRouterSpendService
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class OpenRouterMonitoringJobTest {
  @Test
  fun `refreshes the monthly spend`() {
    val spend = mockk<OpenRouterSpendService>(relaxed = true)
    OpenRouterMonitoringJob(mockk(relaxed = true), spend).refresh()
    verify { spend.refresh() }
  }
}
