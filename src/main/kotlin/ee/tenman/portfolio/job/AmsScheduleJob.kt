package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.kuku.KukuScheduleClient
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.RadioProperties
import ee.tenman.portfolio.service.radio.RadioScheduleService
import jakarta.annotation.PostConstruct
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

abstract class AmsScheduleJob(
  private val station: Station,
  private val domain: String,
  private val kukuScheduleClient: KukuScheduleClient,
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
    val from = LocalDate.now(clock).atStartOfDay(clock.zone).toInstant()
    val until = from + Duration.ofDays(DAYS)
    val days = kukuScheduleClient.getSchedule(domain, from.toString(), until.toString())
    radioScheduleService.replace(station, days.flatMap { it.episodes }.mapNotNull { it.scheduled() }, from, until)
    radioScheduleService.plan()
  }

  companion object {
    const val DAYS = 8L
  }
}
