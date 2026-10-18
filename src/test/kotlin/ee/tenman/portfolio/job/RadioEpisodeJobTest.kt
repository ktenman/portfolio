package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.RadioEpisodeService
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

  @Test
  fun `should keep going after one recording fails`() {
    val first = Files.createDirectory(recordings.resolve("keskpaevatund-2026-10-17"))
    val second = Files.createDirectory(recordings.resolve("keskpaevatund-2026-10-24"))
    val service = mockk<RadioEpisodeService>()
    every { service.make(first) } throws IllegalStateException("Katki")
    every { service.make(second) } returns second.resolve("episode.m4a")
    RadioEpisodeJob(service, RadioProperties(recordings = recordings), mockk<JobExecutionService>()).execute()
    verify { service.make(second) }
  }
}
