package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.JobExecution
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.job.Job
import ee.tenman.portfolio.repository.JobExecutionRepository
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
  @Transactional
  fun executeJob(job: Job) {
    val startTime = Instant.now(clock)
    var status = JobStatus.SUCCESS
    var message: String? = null

    try {
      job.execute()
    } catch (e: Exception) {
      status = JobStatus.FAILURE
      message = e.message
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
      jobExecutionRepository.save(jobExecution)
    }
  }
}
