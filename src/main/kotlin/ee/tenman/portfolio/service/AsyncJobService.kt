package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.AsyncJob
import ee.tenman.portfolio.domain.AsyncJobStatus
import ee.tenman.portfolio.repository.AsyncJobRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.fasterxml.jackson.databind.ObjectMapper

@Service
class AsyncJobService(
    private val asyncJobRepository: AsyncJobRepository,
    private val objectMapper: ObjectMapper
) {

    @Transactional
    fun createJob(jobType: String, parameters: Any? = null, createdBy: String = "system"): AsyncJob {
        val parametersJson = parameters?.let { objectMapper.writeValueAsString(it) }
        return asyncJobRepository.save(
            AsyncJob(
                jobType = jobType,
                parameters = parametersJson,
                createdBy = createdBy
            )
        )
    }

    @Transactional
    fun updateJobProgress(jobId: Long, current: Int, total: Int) {
        asyncJobRepository.findById(jobId).ifPresent { job ->
            job.updateProgress(current, total)
            asyncJobRepository.save(job)
        }
    }

    @Transactional
    fun markJobAsInProgress(jobId: Long) {
        asyncJobRepository.findById(jobId).ifPresent { job ->
            job.markAsInProgress()
            asyncJobRepository.save(job)
        }
    }

    @Transactional
    fun markJobAsCompleted(jobId: Long, result: Any? = null) {
        asyncJobRepository.findById(jobId).ifPresent { job ->
            val resultJson = result?.let { objectMapper.writeValueAsString(it) }
            job.markAsCompleted(resultJson)
            asyncJobRepository.save(job)
        }
    }

    @Transactional
    fun markJobAsFailed(jobId: Long, errorMessage: String) {
        asyncJobRepository.findById(jobId).ifPresent { job ->
            job.markAsFailed(errorMessage)
            asyncJobRepository.save(job)
        }
    }

    @Transactional(readOnly = true)
    fun getJob(jobId: Long): AsyncJob? = asyncJobRepository.findById(jobId).orElse(null)

    @Transactional(readOnly = true)
    fun getActiveJobs(jobType: String): List<AsyncJob> {
        return asyncJobRepository.findByJobTypeAndStatusIn(
            jobType,
            setOf(AsyncJobStatus.PENDING, AsyncJobStatus.IN_PROGRESS)
        )
    }

    @Transactional(readOnly = true)
    fun hasActiveJobs(jobType: String): Boolean = getActiveJobs(jobType).isNotEmpty()

    fun <T> parseParameters(job: AsyncJob, clazz: Class<T>): T? {
        return job.parameters?.let { objectMapper.readValue(it, clazz) }
    }

    fun <T> parseResult(job: AsyncJob, clazz: Class<T>): T? {
        return job.result?.let { objectMapper.readValue(it, clazz) }
    }
}