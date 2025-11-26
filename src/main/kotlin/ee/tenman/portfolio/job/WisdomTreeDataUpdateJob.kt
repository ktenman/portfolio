package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.service.JobTransactionService
import ee.tenman.portfolio.service.WisdomTreeUpdateService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
@ConditionalOnProperty(name = ["scheduling.enabled"], havingValue = "true", matchIfMissing = true)
class WisdomTreeDataUpdateJob(
  private val jobTransactionService: JobTransactionService,
  private val wisdomTreeUpdateService: WisdomTreeUpdateService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = 120000, fixedDelay = Long.MAX_VALUE)
  @Scheduled(cron = "0 55 23 * * ?")
  fun runJob() {
    execute()
  }

  override fun execute() {
    log.info("Executing WisdomTree data update job for WTAI")
    val start = Instant.now()
    val result = runCatching {
      val data = wisdomTreeUpdateService.updateWtaiHoldings()
      val msg = "Successfully updated WTAI holdings: deleted=${data["deleted"]}, created=${data["created"]}"
      log.info(msg)
      msg
    }

    jobTransactionService.saveJobExecution(
      job = this,
      startTime = start,
      endTime = Instant.now(),
      status = if (result.isSuccess) JobStatus.SUCCESS else JobStatus.FAILURE,
      message = result.getOrElse { "Failed to update WTAI holdings: ${it.message}" },
    )
    result.onFailure { log.error("WisdomTree data update job failed", it) }
  }
}
