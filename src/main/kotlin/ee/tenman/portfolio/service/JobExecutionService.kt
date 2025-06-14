package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.job.Job
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class JobExecutionService(
  private val jobTransactionService: JobTransactionService,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun executeJob(job: Job) {
    val startTime = Instant.now(clock)
    var status = JobStatus.SUCCESS
    var message: String? = null

    try {
      log.info("Starting execution of job: ${job.getName()}")
      jobTransactionService.executeJobInTransaction(job)
      log.info("Job ${job.getName()} executed successfully")
    } catch (e: Exception) {
      status = JobStatus.FAILURE
      message = e.message
      log.error("Job ${job.getName()} failed with error: ${e.message}", e)
      throw e
    } finally {
      val endTime = Instant.now(clock)
      val savedJobExecution =
        jobTransactionService.saveJobExecution(
          job = job,
          startTime = startTime,
          endTime = endTime,
          status = status,
          message = message,
        )
      log.info("Job execution record saved: ${savedJobExecution.id}")
    }
  }
}
