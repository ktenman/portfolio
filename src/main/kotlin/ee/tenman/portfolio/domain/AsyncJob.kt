package ee.tenman.portfolio.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "async_job")
data class AsyncJob(
    @Column(nullable = false)
    val jobType: String,

    @Column(columnDefinition = "TEXT")
    val parameters: String? = null,

    @Column(nullable = false)
    val createdBy: String = "system"
) : BaseEntity() {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: AsyncJobStatus = AsyncJobStatus.PENDING
        private set

    @Column(columnDefinition = "TEXT")
    var result: String? = null
        private set

    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null
        private set

    @Column
    var progress: Int = 0
        private set

    @Column
    var totalItems: Int? = null
        private set

    @Column
    var startedAt: Instant? = null
        private set

    @Column
    var completedAt: Instant? = null
        private set

    fun updateProgress(current: Int, total: Int) {
        progress = if (total > 0) (current * 100) / total else 0
        totalItems = total
    }

    fun markAsInProgress() {
        status = AsyncJobStatus.IN_PROGRESS
        startedAt = Instant.now()
    }

    fun markAsCompleted(result: String? = null) {
        status = AsyncJobStatus.COMPLETED
        completedAt = Instant.now()
        this.result = result
        progress = 100
    }

    fun markAsFailed(errorMessage: String) {
        status = AsyncJobStatus.FAILED
        completedAt = Instant.now()
        this.errorMessage = errorMessage
    }

    val duration: Long?
        get() = startedAt?.let { start ->
            completedAt?.let { end ->
                end.epochSecond - start.epochSecond
            }
        }

    val isTerminal: Boolean
        get() = status in setOf(AsyncJobStatus.COMPLETED, AsyncJobStatus.FAILED, AsyncJobStatus.CANCELLED)

    val isActive: Boolean
        get() = status in setOf(AsyncJobStatus.PENDING, AsyncJobStatus.IN_PROGRESS)
}