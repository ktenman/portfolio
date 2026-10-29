package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.split.SplitCheckService
import org.springframework.scheduling.annotation.Scheduled

@ScheduledJob
class SplitCheckJob(
  private val splitCheckService: SplitCheckService,
  private val jobExecutionService: JobExecutionService,
) : Job {
  @Scheduled(cron = "\${scheduling.jobs.split-check-cron:0 0 6 1,15 * *}")
  fun runScheduledJob() {
    jobExecutionService.executeJob(this)
  }

  override fun execute() {
    splitCheckService.check()
  }
}
