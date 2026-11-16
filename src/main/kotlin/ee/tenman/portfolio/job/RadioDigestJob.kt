package ee.tenman.portfolio.job

import ee.tenman.portfolio.model.CollectionSchedules
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.RadioDigestService
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.LocalDate

@ScheduledJob
class RadioDigestJob(
  private val radioDigestService: RadioDigestService,
  private val jobExecutionService: JobExecutionService,
  private val clock: Clock,
) : Job {
  @Scheduled(cron = CollectionSchedules.RADIO_DIGEST_CRON, zone = CollectionSchedules.TIME_ZONE)
  fun runScheduledJob() {
    if (!radioDigestService.due(LocalDate.now(clock))) return
    jobExecutionService.executeJob(this)
  }

  override fun execute() {
    radioDigestService.build(LocalDate.now(clock))
  }
}
