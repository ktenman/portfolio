package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.inAnyOrder
import ch.tutteli.atrium.api.fluent.en_GB.only
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.values
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.JobExecution
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.job.AripaevScheduleJob
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

  private fun failed(
    registry: SimpleMeterRegistry,
    station: String,
  ) = registry
    .get("portfolio.radio.schedule.failed")
    .tag("station", station)
    .gauge()
    .value()

  private fun planned(
    registry: SimpleMeterRegistry,
    station: String,
  ) = registry
    .get("portfolio.radio.plan.timestamp.seconds")
    .tag("station", station)
    .gauge()
    .value()

  private fun bridged(
    station: String,
    at: Instant,
  ) {
    val playlist = Files.createDirectories(directory.resolve(station)).resolve("stream.m3u8")
    Files.setLastModifiedTime(Files.writeString(playlist, "#EXTM3U"), FileTime.from(at))
  }

  private fun bridge(
    registry: SimpleMeterRegistry,
    station: String = "Äripäev",
  ) = registry
    .get("portfolio.radio.bridge.timestamp.seconds")
    .tag("station", station)
    .gauge()
    .value()

  private fun refreshed(
    missed: Map<Station, List<MissedCapture>> = emptyMap(),
    status: JobStatus? = null,
    aripaev: JobStatus? = null,
    renewed: Map<String, Instant> = emptyMap(),
  ): SimpleMeterRegistry {
    every { radioScheduleService.missed() } returns missed
    every { jobExecutionRepository.findFirstByJobNameOrderByStartTimeDesc(any()) } returns null
    every { jobExecutionRepository.findFirstByJobNameOrderByStartTimeDesc("RadioScheduleJob") } returns
      status?.let { JobExecution("RadioScheduleJob", Instant.EPOCH, Instant.EPOCH, 0, it) }
    every { jobExecutionRepository.findFirstByJobNameOrderByStartTimeDesc(ARIPAEV_JOB) } returns
      aripaev?.let { JobExecution(ARIPAEV_JOB, Instant.EPOCH, Instant.EPOCH, 0, it) }
    every { jobExecutionRepository.findFirstByJobNameAndStatusOrderByStartTimeDesc(any(), JobStatus.SUCCESS) } answers {
      renewed[firstArg()]?.let { JobExecution(firstArg(), it, it, 0, JobStatus.SUCCESS) }
    }
    val registry = SimpleMeterRegistry()
    RadioMetricsService(radioScheduleService, jobExecutionRepository, RadioProperties(recordings = directory))
      .also { it.bindTo(registry) }
      .refresh()
    return registry
  }

  @Test
  fun `should export a missed capture with its station programmes and the reason it was missed`() {
    val capture = Capture("2026-10-24-1200-aripaev-kuum-tool", listOf("Kuum tool", "Fookus"), 0, 0, Slot(0, 0))
    val registry = refreshed(missed = mapOf(Station.ARIPAEV to listOf(MissedCapture(capture, MissedReason.DOWN))))
    val gauge =
      registry
        .get("portfolio.radio.missed.capture")
        .tags("station", "Äripäev", "capture", capture.name, "programmes", "Kuum tool, Fookus")
    expect(gauge.gauge().value()).toEqual(2.0)
  }

  @Test
  fun `should report a failed schedule import`() {
    expect(failed(refreshed(status = JobStatus.FAILURE), "Kuku")).toEqual(1.0)
  }

  @Test
  fun `should not report a schedule import that succeeded`() {
    expect(failed(refreshed(status = JobStatus.SUCCESS), "Kuku")).toEqual(0.0)
  }

  @Test
  fun `should name Äripäev when its schedule import failed`() {
    expect(failed(refreshed(status = JobStatus.SUCCESS, aripaev = JobStatus.FAILURE), "Äripäev")).toEqual(1.0)
  }

  @Test
  fun `should leave Kuku unreported when only the schedule import of Äripäev failed`() {
    expect(failed(refreshed(status = JobStatus.SUCCESS, aripaev = JobStatus.FAILURE), "Kuku")).toEqual(0.0)
  }

  @Test
  fun `should export every station before any refresh`() {
    val registry = SimpleMeterRegistry()
    RadioMetricsService(radioScheduleService, jobExecutionRepository, RadioProperties(recordings = directory)).bindTo(registry)
    expect(registry.get("portfolio.radio.schedule.failed").gauges().map { it.id.getTag("station") })
      .toContain.inAnyOrder.only
      .values("Kuku", "Äripäev", "Elmar", "Sky Plus")
  }

  @Test
  fun `should register no gauges for a station that is off`() {
    val registry = SimpleMeterRegistry()
    RadioMetricsService(
      radioScheduleService,
      jobExecutionRepository,
      RadioProperties(recordings = directory, stations = listOf(Station.KUKU)),
    ).bindTo(registry)
    expect(registry.meters.mapNotNull { it.id.getTag("station") }.distinct()).toContainExactly("Kuku")
  }

  @Test
  fun `should report the bridge of each listed station under its own label`() {
    bridged(".skyplus", ARIPAEV_RENEWED)
    expect(bridge(refreshed(), "Sky Plus")).toEqual(1_792_908_000.0)
  }

  @Test
  fun `should keep the bridge of Äripäev silent when only the bridge of Sky Plus writes`() {
    bridged(".skyplus", ARIPAEV_RENEWED)
    expect(bridge(refreshed()).isNaN()).toEqual(true)
  }

  @Test
  fun `should report when the import of each station last renewed its plan`() {
    val registry = refreshed(renewed = mapOf("RadioScheduleJob" to KUKU_RENEWED, ARIPAEV_JOB to ARIPAEV_RENEWED))
    expect(planned(registry, "Kuku") to planned(registry, "Äripäev")).toEqual(1_792_821_600.0 to 1_792_908_000.0)
  }

  @Test
  fun `should report Kuku as never renewed when only the import of Äripäev wrote the plans`() {
    Files.writeString(directory.resolve(Station.KUKU.plan), "[]")
    expect(planned(refreshed(renewed = mapOf(ARIPAEV_JOB to ARIPAEV_RENEWED)), "Kuku")).toEqual(0.0)
  }

  @Test
  fun `should report when the bridge last wrote its playlist`() {
    bridged(".aripaev", KUKU_RENEWED)
    expect(bridge(refreshed())).toEqual(1_792_821_600.0)
  }

  @Test
  fun `should report no bridge time without a playlist or a worker so the alert stays silent`() {
    expect(bridge(refreshed()).isNaN()).toEqual(true)
  }

  @Test
  fun `should report the playlist as never written when the worker of Äripäev runs without the bridge`() {
    Files.writeString(directory.resolve(Station.ARIPAEV.heartbeat), """{"seen":0,"until":0}""")
    expect(bridge(refreshed())).toEqual(0.0)
  }

  @Test
  fun `should report no bridge time before any refresh`() {
    val registry = SimpleMeterRegistry()
    RadioMetricsService(radioScheduleService, jobExecutionRepository, RadioProperties(recordings = directory)).bindTo(registry)
    expect(bridge(registry).isNaN()).toEqual(true)
  }

  private companion object {
    val ARIPAEV_JOB = AripaevScheduleJob(mockk(), mockk(), mockk(), mockk(), mockk(), mockk()).getName()
    val KUKU_RENEWED: Instant = Instant.ofEpochSecond(1_792_821_600)
    val ARIPAEV_RENEWED: Instant = Instant.ofEpochSecond(1_792_908_000)
  }
}
