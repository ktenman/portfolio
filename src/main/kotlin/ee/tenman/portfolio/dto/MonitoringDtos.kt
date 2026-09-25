package ee.tenman.portfolio.dto

import java.time.Instant

enum class CollectionStatus {
  OK,
  DISABLED,
  BREAKER_OPEN,
  OVERDUE,
  PARTIAL_FAILURE,
}

data class CollectionStatusDto(
  val key: String,
  val provider: String,
  val operation: String,
  val status: CollectionStatus,
  val expected: Int,
  val fetched: Int,
  val persisted: Int,
  val failed: Int,
  val failedItems: List<String>,
  val consecutiveEmptyRuns: Int,
  val durationSeconds: Double,
  val lastAttempt: Instant?,
  val lastCompletion: Instant?,
  val lastFullSuccess: Instant?,
  val deadline: Instant?,
  val breakerOpen: Boolean,
)
