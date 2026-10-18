package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.RadioStorageService
import org.springframework.scheduling.annotation.Scheduled

@ScheduledJob
class RadioStorageJob(
  private val radioStorageService: RadioStorageService,
  private val jobExecutionService: JobExecutionService,
) : Job {
  @Scheduled(cron = "\${scheduling.jobs.radio-storage-cron:0 45 * * * *}")
  fun runScheduledJob() {
    jobExecutionService.executeJob(this)
  }

  override fun execute() = radioStorageService.store()
}
