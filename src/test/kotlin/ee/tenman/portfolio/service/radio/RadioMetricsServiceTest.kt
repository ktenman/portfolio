package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.JobExecution
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.repository.JobExecutionRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant

class RadioMetricsServiceTest {
  @TempDir
  lateinit var directory: Path

  private val radioScheduleService = mockk<RadioScheduleService>()

  private val jobExecutionRepository = mockk<JobExecutionRepository>()

  private fun refreshed(
    missed: List<MissedCapture> = emptyList(),
    status: JobStatus? = null,
  ): SimpleMeterRegistry {
    every { radioScheduleService.missed() } returns missed
    every { jobExecutionRepository.findFirstByJobNameOrderByStartTimeDesc("RadioScheduleJob") } returns
      status?.let { JobExecution("RadioScheduleJob", Instant.EPOCH, Instant.EPOCH, 0, it) }
    val registry = SimpleMeterRegistry()
    RadioMetricsService(radioScheduleService, jobExecutionRepository, RadioProperties(recordings = directory))
      .also { it.bindTo(registry) }
      .refresh()
    return registry
  }

  @Test
  fun `should export a missed capture with its programmes and the reason it was missed`() {
    val capture = Capture("2026-10-24-1200-keskpaevatund", listOf("Keskpäevatund", "Tarkade klubi"), 0, 0, Slot(0, 0))
    val registry = refreshed(missed = listOf(MissedCapture(capture, MissedReason.DOWN)))
    val gauge = registry.get("portfolio.radio.missed.capture").tags("capture", capture.name, "programmes", "Keskpäevatund, Tarkade klubi")
    expect(gauge.gauge().value()).toEqual(2.0)
  }

  @Test
  fun `should report a failed schedule import`() {
    expect(refreshed(status = JobStatus.FAILURE).get("portfolio.radio.schedule.failed").gauge().value()).toEqual(1.0)
  }

  @Test
  fun `should not report a schedule import that succeeded`() {
    expect(refreshed(status = JobStatus.SUCCESS).get("portfolio.radio.schedule.failed").gauge().value()).toEqual(0.0)
  }

  @Test
  fun `should report when the plan was last written`() {
    Files.writeString(directory.resolve(Station.KUKU.plan), "[]").also {
      Files.setLastModifiedTime(it, FileTime.from(Instant.ofEpochSecond(1_792_821_600)))
    }
    expect(refreshed().get("portfolio.radio.plan.timestamp.seconds").gauge().value()).toEqual(1_792_821_600.0)
  }

  @Test
  fun `should report a missing plan as never written`() {
    expect(refreshed().get("portfolio.radio.plan.timestamp.seconds").gauge().value()).toEqual(0.0)
  }
}
