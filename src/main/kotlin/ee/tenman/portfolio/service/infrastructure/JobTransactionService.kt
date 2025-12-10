package ee.tenman.portfolio.service.infrastructure

import ee.tenman.portfolio.domain.JobExecution
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.job.Job
import ee.tenman.portfolio.repository.JobExecutionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class JobTransactionService(
  private val jobExecutionRepository: JobExecutionRepository,
) {
  fun executeJobInTransaction(job: Job) {
    job.execute()
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun saveJobExecution(
    job: Job,
    startTime: Instant,
    endTime: Instant,
    status: JobStatus,
    message: String?,
  ): JobExecution {
    val duration = Duration.between(startTime, endTime)
    val jobExecution =
      JobExecution(
        jobName = job.getName(),
        startTime = startTime,
        endTime = endTime,
        durationInMillis = duration.toMillis(),
        status = status,
        message = message,
      )
    return jobExecutionRepository.save(jobExecution)
  }
}
