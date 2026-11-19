package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.RadioStorageService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.nio.file.Path

class RadioRawJobTest {
  @Test
  fun `should not record a run when no recording is due`() {
    val storage = mockk<RadioStorageService> { every { due() } returns emptyList() }
    val executions = mockk<JobExecutionService>(relaxed = true)
    RadioRawJob(storage, executions).runScheduledJob()
    verify(exactly = 0) { executions.executeJob(any()) }
  }

  @Test
  fun `should record a run when a recording is due`() {
    val storage = mockk<RadioStorageService> { every { due() } returns listOf(Path.of("2026-11-18-1200-kuku")) }
    val executions = mockk<JobExecutionService>(relaxed = true)
    val job = RadioRawJob(storage, executions)
    job.runScheduledJob()
    verify { executions.executeJob(job) }
  }
}
