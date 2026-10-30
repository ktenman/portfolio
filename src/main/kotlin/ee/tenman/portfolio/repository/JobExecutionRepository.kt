package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.JobExecution
import ee.tenman.portfolio.domain.JobStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface JobExecutionRepository : JpaRepository<JobExecution, Long> {
  fun findFirstByJobNameOrderByStartTimeDesc(jobName: String): JobExecution?

  fun findFirstByJobNameAndStatusOrderByStartTimeDesc(
    jobName: String,
    status: JobStatus,
  ): JobExecution?
}
