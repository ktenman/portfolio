package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.JobStatus
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

  @Volatile private var planned = 0.0

  @Volatile private var failed = 0.0

  override fun bindTo(registry: MeterRegistry) {
    missed = MultiGauge.builder("portfolio.radio.missed.capture").register(registry)
    Gauge.builder("portfolio.radio.plan.timestamp.seconds", this) { it.planned }.register(registry)
    Gauge.builder("portfolio.radio.schedule.failed", this) { it.failed }.register(registry)
  }

  fun refresh() {
    if (!::missed.isInitialized) return
    missed.register(radioScheduleService.missed().map(::row), true)
    val plan = radioProperties.recordings.resolve(PLAN_FILE)
    planned = plan.takeIf(Files::exists)?.let {
      Files
      .getLastModifiedTime(it)
      .toInstant()
      .epochSecond
      .toDouble()
    } ?: 0.0
    failed = if (jobExecutionRepository.findFirstByJobNameOrderByStartTimeDesc(SCHEDULE_JOB)?.status == JobStatus.FAILURE) 1.0 else 0.0
  }

  private fun row(capture: MissedCapture): MultiGauge.Row<*> =
    MultiGauge.Row.of(
      Tags.of("capture", capture.capture.name, "programmes", capture.capture.programmes.joinToString(", ")),
      capture.reason.code,
    )

  companion object {
    private const val SCHEDULE_JOB = "RadioScheduleJob"
  }
}
