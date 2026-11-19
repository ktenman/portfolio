package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.RadioStorageService
import org.springframework.scheduling.annotation.Scheduled

@ScheduledJob
class RadioRawJob(
  private val radioStorageService: RadioStorageService,
  private val jobExecutionService: JobExecutionService,
) : Job {
  @Scheduled(fixedDelayString = "\${scheduling.jobs.radio-raw-delay:15000}")
  fun runScheduledJob() {
    if (radioStorageService.due().isEmpty()) return
    jobExecutionService.executeJob(this)
  }

  override fun execute() = radioStorageService.drop()
}
