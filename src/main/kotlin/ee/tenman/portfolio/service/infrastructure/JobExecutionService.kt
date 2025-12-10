package ee.tenman.portfolio.service.infrastructure

import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.job.Job
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class JobExecutionService(
  private val jobTransactionService: JobTransactionService,
  private val clock: Clock,
  private val circuitBreakerRegistry: CircuitBreakerRegistry,
  private val retryRegistry: RetryRegistry,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun executeJob(job: Job) {
    val jobName = job.getName()
    val circuitBreaker = circuitBreakerRegistry.circuitBreaker("job-execution")
    val retry = retryRegistry.retry("job-execution")
    val startTime = Instant.now(clock)

    if (circuitBreaker.state == CircuitBreaker.State.OPEN) {
      log.warn("Circuit breaker is OPEN for job execution. Skipping job: $jobName")
      jobTransactionService.saveJobExecution(
        job = job,
        startTime = startTime,
        endTime = Instant.now(clock),
        status = JobStatus.SKIPPED,
        message = "Circuit breaker is OPEN",
      )
      return
    }

    var status = JobStatus.SUCCESS
    var message: String? = null

    try {
      log.info("Starting execution of job: $jobName")

      retry.executeSupplier {
        circuitBreaker.executeSupplier {
          jobTransactionService.executeJobInTransaction(job)
        }
      }

      log.info("Job $jobName executed successfully")
    } catch (e: Exception) {
      status = JobStatus.FAILURE
      message = e.message
      log.error("Job $jobName failed with error: ${e.message}", e)
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
