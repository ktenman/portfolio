package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.RadioEpisodeService
import ee.tenman.portfolio.service.radio.RadioIdentService
import ee.tenman.portfolio.service.radio.RadioProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RadioEpisodeJobTest {
  @TempDir
  lateinit var recordings: Path

  private val idents = mockk<RadioIdentService>(relaxed = true)

  @Test
  fun `should keep going after one recording fails`() {
    val first = Files.createDirectory(recordings.resolve("keskpaevatund-2026-10-17"))
    val second = Files.createDirectory(recordings.resolve("keskpaevatund-2026-10-24"))
    val service = mockk<RadioEpisodeService>()
    every { service.make(first) } throws IllegalStateException("Katki")
    every { service.make(second) } returns false
    RadioEpisodeJob(service, idents, RadioProperties(recordings = recordings), mockk<JobExecutionService>()).execute()
    verify { service.make(second) }
  }

  @Test
  fun `should save the jingle of a recording it cut`() {
    val cut = Files.createDirectory(recordings.resolve("2026-10-24-1155-keskpaevatund"))
    val service = mockk<RadioEpisodeService> { every { make(cut) } returns true }
    RadioEpisodeJob(service, idents, RadioProperties(recordings = recordings), mockk<JobExecutionService>()).execute()
    verify { idents.save(cut) }
  }

  @Test
  fun `should save no jingle of a recording it left alone`() {
    val left = Files.createDirectory(recordings.resolve("2026-10-24-1155-keskpaevatund"))
    val service = mockk<RadioEpisodeService> { every { make(left) } returns false }
    RadioEpisodeJob(service, idents, RadioProperties(recordings = recordings), mockk<JobExecutionService>()).execute()
    verify(exactly = 0) { idents.save(any()) }
  }

  @Test
  fun `should not record a run when no recording has cuts left to find`() {
    Files.createDirectory(recordings.resolve("2026-10-24-1155-keskpaevatund"))
    val service = mockk<RadioEpisodeService> { every { todo(any()) } returns emptyList() }
    val executions = mockk<JobExecutionService>(relaxed = true)
    RadioEpisodeJob(service, idents, RadioProperties(recordings = recordings), executions).runScheduledJob()
    verify(exactly = 0) { executions.executeJob(any()) }
  }

  @Test
  fun `should record a run when a recording has cuts left to find`() {
    Files.createDirectory(recordings.resolve("2026-10-24-1155-keskpaevatund"))
    val service = mockk<RadioEpisodeService> { every { todo(any()) } returns listOf("Keskpäevatund") }
    val executions = mockk<JobExecutionService>(relaxed = true)
    val job = RadioEpisodeJob(service, idents, RadioProperties(recordings = recordings), executions)
    job.runScheduledJob()
    verify { executions.executeJob(job) }
  }

  @Test
  fun `should look past a recording it cannot read for cuts left to find`() {
    val broken = Files.createDirectory(recordings.resolve("2026-10-17-1155-keskpaevatund"))
    val waiting = Files.createDirectory(recordings.resolve("2026-10-24-1155-keskpaevatund"))
    val service = mockk<RadioEpisodeService>()
    every { service.todo(broken) } throws IllegalStateException("Katki status.json")
    every { service.todo(waiting) } returns listOf("Keskpäevatund")
    val executions = mockk<JobExecutionService>(relaxed = true)
    val job = RadioEpisodeJob(service, idents, RadioProperties(recordings = recordings), executions)
    job.runScheduledJob()
    verify { executions.executeJob(job) }
  }
}
