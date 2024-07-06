package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.JobExecution
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.job.Job
import ee.tenman.portfolio.repository.JobExecutionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Service
class JobExecutionService(
  private val jobExecutionRepository: JobExecutionRepository,
  private val clock: Clock
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  fun executeJob(job: Job) {
    val startTime = Instant.now(clock)
    var status = JobStatus.SUCCESS
    var message: String? = null

    try {
      log.info("Starting execution of job: ${job.getName()}")
      job.execute()
      log.info("Job ${job.getName()} executed successfully")
    } catch (e: Exception) {
      status = JobStatus.FAILURE
      message = e.message
      log.error("Job ${job.getName()} failed with error: ${e.message}", e)
      throw e
    } finally {
      val endTime = Instant.now(clock)
      val duration = Duration.between(startTime, endTime)
      val jobExecution = JobExecution(
        jobName = job.getName(),
        startTime = startTime,
        endTime = endTime,
        durationInMillis = duration.toMillis(),
        status = status,
        message = message
      )
      val savedJobExecution = jobExecutionRepository.save(jobExecution)
      log.info("Job execution record saved: ${savedJobExecution.id}")
    }
  }
}
