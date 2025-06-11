package ee.tenman.portfolio.domain

enum class AsyncJobStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}