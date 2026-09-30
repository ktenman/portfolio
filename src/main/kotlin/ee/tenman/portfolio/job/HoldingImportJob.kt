package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.etf.HoldingImportService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import org.springframework.scheduling.annotation.Scheduled

@ScheduledJob
class HoldingImportJob(
  private val holdingImportService: HoldingImportService,
  private val jobExecutionService: JobExecutionService,
) : Job {
  @Scheduled(fixedDelay = 60000)
  fun runJob() {
    if (!holdingImportService.hasPending()) return
    jobExecutionService.executeJob(this)
  }

  override fun execute() {
    holdingImportService.importPending()
  }
}
