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

  @Scheduled(cron = "0 55 23 * * ?")
  fun runJob() {
    execute()
  }

  override fun execute() {
    log.info("Executing WisdomTree data update job for WTAI")
    val startTime = Instant.now()
    var status = JobStatus.SUCCESS
    var message: String? = null

    try {
      val result = wisdomTreeUpdateService.updateWtaiHoldings()
      message =
        "Successfully updated WTAI holdings: " +
          "deleted=${result["deleted"]}, created=${result["created"]}"
      log.info(message)
    } catch (e: Exception) {
      status = JobStatus.FAILURE
      message = "Failed to update WTAI holdings: ${e.message}"
      log.error("WisdomTree data update job failed", e)
    } finally {
      val endTime = Instant.now()
      jobTransactionService.saveJobExecution(
        job = this,
        startTime = startTime,
        endTime = endTime,
        status = status,
        message = message,
      )
    }
  }
}
