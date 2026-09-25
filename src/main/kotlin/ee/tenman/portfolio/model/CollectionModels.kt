package ee.tenman.portfolio.model

import ee.tenman.portfolio.domain.CollectionKey
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant

data class CollectionSnapshot(
  val key: CollectionKey,
  val initializedAt: Instant,
  val expected: Set<String>,
  val attempted: Int,
  val fetched: Int,
  val persisted: Int,
  val failed: Int,
  val lastAttempt: Instant?,
  val lastCompletion: Instant?,
  val lastPersistence: Instant?,
  val lastFullSuccess: Instant?,
  val consecutiveEmptyRuns: Int,
  val durationSeconds: Double,
  val itemSuccesses: Map<String, Instant?>,
  val itemInitializedAt: Map<String, Instant> = emptyMap(),
)

data class CollectionRunResult(
  val expected: Set<String>,
  val attempted: Set<String>,
  val fetched: Set<String>,
  val persisted: Set<String>,
  val failed: Set<String>,
  val failures: Map<String, Throwable>,
)

class CollectionRun(
  symbols: Collection<String>,
  private val onPersisted: (String) -> Unit = {},
) {
  private val expected = symbols.toSet()
  private val attempted = mutableSetOf<String>()
  private val fetched = mutableSetOf<String>()
  private val persisted = mutableSetOf<String>()
  private val failures = mutableMapOf<String, Throwable>()

  @Synchronized
  fun attempted(symbol: String) {
    if (symbol in expected) attempted.add(symbol)
  }

  @Synchronized
  fun fetched(symbol: String) {
    if (symbol !in expected) return
    attempted.add(symbol)
    fetched.add(symbol)
  }

  @Synchronized
  fun persisted(symbol: String) {
    if (symbol !in expected) return
    check(!TransactionSynchronizationManager.isActualTransactionActive()) {
      "Collection persistence must be recorded after the transaction commits"
    }
    if (symbol in persisted) return
    onPersisted(symbol)
    attempted.add(symbol)
    fetched.add(symbol)
    persisted.add(symbol)
    failures.remove(symbol)
  }

  @Synchronized
  fun failed(
    symbol: String,
    error: Throwable,
  ) {
    if (symbol !in expected || symbol in persisted) return
    attempted.add(symbol)
    failures.putIfAbsent(symbol, error)
  }

  @Synchronized
  fun result(): CollectionRunResult =
    CollectionRunResult(
      expected = expected,
      attempted = attempted.toSet(),
      fetched = fetched.toSet(),
      persisted = persisted.toSet(),
      failed = expected - persisted,
      failures = failures.toMap(),
    )
}
