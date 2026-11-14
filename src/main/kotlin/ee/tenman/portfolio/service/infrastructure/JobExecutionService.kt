package ee.tenman.portfolio.service.infrastructure

import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.job.Job
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
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

  @EventListener(ApplicationReadyEvent::class)
  fun register(event: ApplicationReadyEvent) =
    event.applicationContext
    .getBeansOfType(Job::class.java)
    .values
    .forEach(::breaker)

  fun executeJob(job: Job) {
    val jobName = job.getName()
    val circuitBreaker = breaker(job)
    val retry = retryRegistry.retry("job-execution")
    val startTime = Instant.now(clock)
    var status = JobStatus.SUCCESS
    var message: String? = null

    try {
      log.info("Starting execution of job: $jobName")

      circuitBreaker.executeSupplier {
        retry.executeSupplier {
          jobTransactionService.executeJobInTransaction(job)
        }
      }

      log.info("Job $jobName executed successfully")
    } catch (e: CallNotPermittedException) {
      status = JobStatus.SKIPPED
      message = e.message
      log.warn("Skipping job $jobName: ${e.message}")
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

  private fun breaker(job: Job): CircuitBreaker = circuitBreakerRegistry.circuitBreaker("job-execution:${job.getName()}")
}
