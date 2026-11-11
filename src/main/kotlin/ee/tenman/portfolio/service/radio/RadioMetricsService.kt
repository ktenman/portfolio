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

  @Volatile private var bridged = emptyMap<Station, Double>()

  override fun bindTo(registry: MeterRegistry) {
    missed = MultiGauge.builder("portfolio.radio.missed.capture").register(registry)
    radioProperties.stations.filter { it.playlist != null }.forEach { station ->
      Gauge
        .builder("portfolio.radio.bridge.timestamp.seconds", this) { it.bridged[station] ?: Double.NaN }
        .tag("station", station.label)
        .register(registry)
    }
    radioProperties.stations.forEach { station ->
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
    val jobs = jobs()
    planned = jobs.mapValues { (_, job) -> renewed(job) }
    failed = jobs.filterValues(::failed).keys
    bridged = radioProperties.stations.associateWith(::playlist)
  }

  private fun jobs(): Map<Station, String> = SCHEDULE_JOBS.filterKeys { it in radioProperties.stations }

  private fun playlist(station: Station): Double {
    val root = radioProperties.recordings
    val playlist = station.playlist?.let(root::resolve) ?: return Double.NaN
    if (Files.exists(playlist)) return Files.getLastModifiedTime(playlist).toMillis() / 1000.0
    return if (Files.exists(root.resolve(station.heartbeat))) 0.0 else Double.NaN
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
    private val SCHEDULE_JOBS =
      mapOf(
        Station.KUKU to "RadioScheduleJob",
        Station.ARIPAEV to "AripaevScheduleJob",
        Station.ELMAR to "ElmarScheduleJob",
        Station.SKYPLUS to "SkyplusScheduleJob",
      )
  }
}
