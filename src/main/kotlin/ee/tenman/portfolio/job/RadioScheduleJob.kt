package ee.tenman.portfolio.job

import ee.tenman.portfolio.kuku.KukuScheduleClient
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.RadioScheduleService
import jakarta.annotation.PostConstruct
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

@ScheduledJob
class RadioScheduleJob(
  private val kukuScheduleClient: KukuScheduleClient,
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
    val from = LocalDate.now(clock).atStartOfDay(clock.zone).toInstant()
    val until = from + Duration.ofDays(DAYS)
    val episodes = kukuScheduleClient.getSchedule(DOMAIN, from.toString(), until.toString()).flatMap { it.episodes }
    radioScheduleService.replace(episodes, from, until)
    radioScheduleService.plan()
  }

  companion object {
    private const val DOMAIN = "kuku.pleier.ee"
    private const val DAYS = 8L
  }
}
