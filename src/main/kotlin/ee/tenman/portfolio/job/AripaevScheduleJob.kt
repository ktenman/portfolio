package ee.tenman.portfolio.job

import ee.tenman.portfolio.aripaev.AripaevScheduleClient
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.RadioScheduleService
import ee.tenman.portfolio.service.radio.aripaevAirings
import jakarta.annotation.PostConstruct
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock

@ScheduledJob
class AripaevScheduleJob(
  private val aripaevScheduleClient: AripaevScheduleClient,
  private val radioScheduleService: RadioScheduleService,
  private val jobExecutionService: JobExecutionService,
  private val taskScheduler: TaskScheduler,
  private val clock: Clock,
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
    val airings = aripaevAirings(aripaevScheduleClient.getSchedule(), clock.zone)
    radioScheduleService.replace(Station.ARIPAEV, airings, airings.minOf { it.start }, airings.maxOf { it.end })
    radioScheduleService.plan()
  }
}
