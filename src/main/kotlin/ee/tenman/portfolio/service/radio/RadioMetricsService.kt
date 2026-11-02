package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.repository.JobExecutionRepository
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Service
import java.nio.file.Files

@Service
class RadioMetricsService(
  private val radioScheduleService: RadioScheduleService,
  private val jobExecutionRepository: JobExecutionRepository,
  private val radioProperties: RadioProperties,
) : MeterBinder {
  private lateinit var missed: MultiGauge

  @Volatile private var planned = emptyMap<Station, Double>()

  @Volatile private var failed = emptySet<Station>()

  @Volatile private var bridged = Double.NaN

  override fun bindTo(registry: MeterRegistry) {
    missed = MultiGauge.builder("portfolio.radio.missed.capture").register(registry)
    Gauge.builder("portfolio.radio.bridge.timestamp.seconds", this) { it.bridged }.register(registry)
    SCHEDULE_JOBS.keys.forEach { station ->
      Gauge
        .builder("portfolio.radio.plan.timestamp.seconds", this) { it.planned[station] ?: 0.0 }
        .tag("station", station.label)
        .register(registry)
      Gauge
        .builder("portfolio.radio.schedule.failed", this) { if (station in it.failed) 1.0 else 0.0 }
        .tag("station", station.label)
        .register(registry)
    }
  }

  fun refresh() {
    if (!::missed.isInitialized) return
    missed.register(radioScheduleService.missed().flatMap { (station, captures) -> captures.map { row(station, it) } }, true)
    planned = SCHEDULE_JOBS.mapValues { (_, job) -> renewed(job) }
    failed = SCHEDULE_JOBS.filterValues(::failed).keys
    bridged = playlist()
  }

  private fun playlist(): Double {
    val root = radioProperties.recordings
    val playlist = root.resolve(BRIDGE_PLAYLIST)
    if (Files.exists(playlist)) return Files.getLastModifiedTime(playlist).toMillis() / 1000.0
    return if (Files.exists(root.resolve(Station.ARIPAEV.heartbeat))) 0.0 else Double.NaN
  }

  private fun renewed(job: String): Double =
    jobExecutionRepository
      .findFirstByJobNameAndStatusOrderByStartTimeDesc(job, JobStatus.SUCCESS)
      ?.endTime
      ?.epochSecond
      ?.toDouble() ?: 0.0

  private fun failed(job: String): Boolean = jobExecutionRepository.findFirstByJobNameOrderByStartTimeDesc(job)?.status == JobStatus.FAILURE

  private fun row(
    station: Station,
    capture: MissedCapture,
  ): MultiGauge.Row<*> =
    MultiGauge.Row.of(
      Tags.of("station", station.label, "capture", capture.capture.name, "programmes", capture.capture.programmes.joinToString(", ")),
      capture.reason.code,
    )

  companion object {
    private const val BRIDGE_PLAYLIST = ".aripaev/stream.m3u8"
    private val SCHEDULE_JOBS = mapOf(Station.KUKU to "RadioScheduleJob", Station.ARIPAEV to "AripaevScheduleJob")
  }
}
