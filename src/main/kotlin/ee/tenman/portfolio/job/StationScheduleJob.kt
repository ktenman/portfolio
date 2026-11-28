package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.err.ErrScheduleClient
import ee.tenman.portfolio.err.ErrScheduleItem
import ee.tenman.portfolio.kuku.KukuScheduleClient
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.RadioProperties
import ee.tenman.portfolio.service.radio.RadioScheduleService
import ee.tenman.portfolio.service.radio.ScheduledAiring
import ee.tenman.portfolio.service.radio.errAirings
import jakarta.annotation.PostConstruct
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

abstract class StationScheduleJob(
  protected val station: Station,
  private val radioScheduleService: RadioScheduleService,
  private val jobExecutionService: JobExecutionService,
  private val taskScheduler: TaskScheduler,
  private val clock: Clock,
  private val radioProperties: RadioProperties,
) : Job {
  @PostConstruct
  fun scheduleInitialRun() {
    taskScheduler.schedule({ runScheduledJob() }, clock.instant().plusSeconds(5))
  }

  @Scheduled(cron = "\${scheduling.jobs.radio-schedule-cron:0 30 3 * * *}")
  fun runScheduledJob() {
    jobExecutionService.executeJob(this)
  }

  override fun execute() {
    if (station !in radioProperties.stations) return
    val today = LocalDate.now(clock)
    val midnights = (0..DAYS).map { today.plusDays(it).atStartOfDay(clock.zone).toInstant() }
    radioScheduleService.replace(station, airings(midnights), midnights.first(), midnights.last())
    radioScheduleService.plan()
  }

  protected abstract fun airings(midnights: List<Instant>): List<ScheduledAiring>

  companion object {
    const val DAYS = 8L
  }
}

abstract class AmsScheduleJob(
  station: Station,
  private val domain: String,
  private val kukuScheduleClient: KukuScheduleClient,
  radioScheduleService: RadioScheduleService,
  jobExecutionService: JobExecutionService,
  taskScheduler: TaskScheduler,
  clock: Clock,
  radioProperties: RadioProperties,
) : StationScheduleJob(station, radioScheduleService, jobExecutionService, taskScheduler, clock, radioProperties) {
  override fun airings(midnights: List<Instant>): List<ScheduledAiring> =
    kukuScheduleClient
      .getSchedule(domain, midnights.first().toString(), midnights.last().toString())
      .flatMap { it.episodes }
      .mapNotNull { it.scheduled() }
}

abstract class ErrScheduleJob(
  station: Station,
  private val errScheduleClient: ErrScheduleClient,
  radioScheduleService: RadioScheduleService,
  jobExecutionService: JobExecutionService,
  taskScheduler: TaskScheduler,
  clock: Clock,
  radioProperties: RadioProperties,
) : StationScheduleJob(station, radioScheduleService, jobExecutionService, taskScheduler, clock, radioProperties) {
  override fun airings(midnights: List<Instant>): List<ScheduledAiring> = errAirings(station, midnights.dropLast(1).flatMap(::items))

  private fun items(midnight: Instant): List<ErrScheduleItem> =
    errScheduleClient
      .getSchedule(station.name.lowercase(), midnight.epochSecond)
      .data.channels
      .flatMap { it.items }
}
