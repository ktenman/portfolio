package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.split.SplitCheckService
import ee.tenman.portfolio.service.split.SplitMetricsService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test

class SplitJobsTest {
  @Test
  fun `should run the split check through the job execution service`() {
    val jobExecutionService = mockk<JobExecutionService>()
    val job = SplitCheckJob(mockk(), jobExecutionService)
    every { jobExecutionService.executeJob(job) } just runs
    job.runScheduledJob()
    verify { jobExecutionService.executeJob(job) }
  }

  @Test
  fun `should check for announced splits when the job executes`() {
    val splitCheckService = mockk<SplitCheckService>()
    every { splitCheckService.check() } just runs
    SplitCheckJob(splitCheckService, mockk()).execute()
    verify { splitCheckService.check() }
  }

  @Test
  fun `should refresh the split metrics`() {
    val splitMetricsService = mockk<SplitMetricsService>()
    every { splitMetricsService.refresh() } just runs
    SplitMonitoringJob(splitMetricsService).refresh()
    verify { splitMetricsService.refresh() }
  }
}
