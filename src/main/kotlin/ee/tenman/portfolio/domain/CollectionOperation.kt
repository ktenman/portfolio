package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "collection_operation")
class CollectionOperation(
  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "collection_key", nullable = false)
  val key: CollectionKey,
  @Column(name = "initialized_at", nullable = false)
  val initializedAt: Instant,
  @Column(name = "attempted_items", nullable = false)
  var attempted: Int = 0,
  @Column(name = "fetched_items", nullable = false)
  var fetched: Int = 0,
  @Column(name = "persisted_items", nullable = false)
  var persisted: Int = 0,
  @Column(name = "failed_items", nullable = false)
  var failed: Int = 0,
  @Column(name = "last_attempt")
  var lastAttempt: Instant? = null,
  @Column(name = "last_completion")
  var lastCompletion: Instant? = null,
  @Column(name = "last_persistence")
  var lastPersistence: Instant? = null,
  @Column(name = "last_full_success")
  var lastFullSuccess: Instant? = null,
  @Column(name = "consecutive_empty_runs", nullable = false)
  var consecutiveEmptyRuns: Int = 0,
  @Column(name = "duration_seconds", nullable = false)
  var durationSeconds: Double = 0.0,
)
