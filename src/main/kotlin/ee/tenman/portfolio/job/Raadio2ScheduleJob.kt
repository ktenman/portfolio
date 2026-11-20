package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.err.ErrScheduleClient
import ee.tenman.portfolio.err.ErrScheduleItem
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.RadioProperties
import ee.tenman.portfolio.service.radio.RadioScheduleService
import ee.tenman.portfolio.service.radio.raadio2Airings
import jakarta.annotation.PostConstruct
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@ScheduledJob
class Raadio2ScheduleJob(
  private val errScheduleClient: ErrScheduleClient,
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
    if (Station.RAADIO2 !in radioProperties.stations) return
    val today = LocalDate.now(clock)
    val midnights = (0..AmsScheduleJob.DAYS).map { today.plusDays(it).atStartOfDay(clock.zone).toInstant() }
    val airings = raadio2Airings(midnights.dropLast(1).flatMap(::items))
    radioScheduleService.replace(Station.RAADIO2, airings, midnights.first(), midnights.last())
    radioScheduleService.plan()
  }

  private fun items(midnight: Instant): List<ErrScheduleItem> =
    errScheduleClient
      .getSchedule(midnight.epochSecond)
      .data.channels
      .flatMap { it.items }
}
