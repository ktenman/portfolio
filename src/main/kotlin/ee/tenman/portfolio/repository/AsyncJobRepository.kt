package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.AsyncJob
import ee.tenman.portfolio.domain.AsyncJobStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface AsyncJobRepository : JpaRepository<AsyncJob, Long> {
    fun findByStatus(status: AsyncJobStatus): List<AsyncJob>
    fun findByStatusIn(statuses: Set<AsyncJobStatus>): List<AsyncJob>
    fun findByJobTypeAndStatusIn(jobType: String, statuses: Set<AsyncJobStatus>): List<AsyncJob>
}