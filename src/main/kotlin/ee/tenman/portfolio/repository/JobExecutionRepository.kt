package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.JobExecution
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface JobExecutionRepository : JpaRepository<JobExecution, Long>
