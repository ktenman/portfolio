package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.RadioProperties
import ee.tenman.portfolio.service.radio.RadioScheduleService
import ee.tenman.portfolio.service.radio.skyplusAirings
import ee.tenman.portfolio.skyplus.SkyplusScheduleClient
import jakarta.annotation.PostConstruct
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

@ScheduledJob
class SkyplusScheduleJob(
  private val skyplusScheduleClient: SkyplusScheduleClient,
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
    if (Station.SKYPLUS !in radioProperties.stations) return
    val today = LocalDate.now(clock)
    val from = today.atStartOfDay(clock.zone).toInstant()
    val airings = skyplusAirings(skyplusScheduleClient.getSchedule().data, today, AmsScheduleJob.DAYS, clock.zone)
    radioScheduleService.replace(Station.SKYPLUS, airings, from, from + Duration.ofDays(AmsScheduleJob.DAYS))
    radioScheduleService.plan()
  }
}
