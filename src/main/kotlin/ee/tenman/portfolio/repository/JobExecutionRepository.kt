package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.JobExecution
import ee.tenman.portfolio.domain.JobStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface JobExecutionRepository : JpaRepository<JobExecution, Long> {
  fun findFirstByJobNameOrderByStartTimeDesc(jobName: String): JobExecution?

  fun findFirstByJobNameAndStatusOrderByStartTimeDesc(
    jobName: String,
    status: JobStatus,
  ): JobExecution?

  fun countByJobNameAndStatusAndStartTimeAfter(
    jobName: String,
    status: JobStatus,
    startTime: Instant,
  ): Long
}
