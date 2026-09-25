package ee.tenman.portfolio.service.monitoring

import ee.tenman.portfolio.domain.CollectionItem
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.domain.CollectionOperation
import ee.tenman.portfolio.model.CollectionRunResult
import ee.tenman.portfolio.model.CollectionSnapshot
import ee.tenman.portfolio.repository.CollectionItemRepository
import ee.tenman.portfolio.repository.CollectionOperationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Service
class CollectionStateService(
  private val operationRepository: CollectionOperationRepository,
  private val itemRepository: CollectionItemRepository,
  private val clock: Clock,
) {
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun initialize(
    key: CollectionKey,
    symbols: Collection<String>,
  ): CollectionSnapshot {
    val now = clock.instant()
    val expected = symbols.toSet()
    operationRepository.insertIfMissing(key.name, now)
    val operation = requireNotNull(operationRepository.findLockedByKey(key))
    val items = itemRepository.findAllByKey(key)
    items.forEach { it.active = it.symbol in expected }
    val existing = items.mapTo(mutableSetOf()) { it.symbol }
    val added = (expected - existing).map { CollectionItem(key, it, now) }
    itemRepository.saveAll(added)
    return snapshot(operation, items + added)
  }

  @Transactional(readOnly = true)
  fun snapshots(): List<CollectionSnapshot> {
    val items = itemRepository.findAll().groupBy { it.key }
    return operationRepository.findAll().map { snapshot(it, items[it.key].orEmpty()) }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun begin(key: CollectionKey): Instant {
    val operation = requireNotNull(operationRepository.findLockedByKey(key))
    val now = clock.instant()
    operation.lastAttempt = later(operation.lastAttempt, now)
    return now
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun recordPersistence(
    key: CollectionKey,
    symbol: String,
  ) {
    val operation = requireNotNull(operationRepository.findLockedByKey(key))
    val item = itemRepository.findByKeyAndSymbolAndActiveTrue(key, symbol)
    requireNotNull(item) { "Collection item $symbol is not active for ${key.name}" }
    val now = clock.instant()
    item.lastSuccess = later(item.lastSuccess, now)
    operation.lastPersistence = later(operation.lastPersistence, now)
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun finish(
    key: CollectionKey,
    started: Instant,
    result: CollectionRunResult,
    succeeded: Boolean,
  ) {
    val operation = requireNotNull(operationRepository.findLockedByKey(key))
    val now = clock.instant()
    operation.lastFullSuccess =
      if (succeeded && result.expected.isNotEmpty() && result.failed.isEmpty()) {
        later(operation.lastFullSuccess, now)
      } else {
        operation.lastFullSuccess
      }
    if (operation.lastCompletion != null && now.isBefore(operation.lastCompletion)) return
    operation.attempted = result.attempted.size
    operation.fetched = result.fetched.size
    operation.persisted = result.persisted.size
    operation.failed = result.failed.size
    operation.consecutiveEmptyRuns = if (result.persisted.isEmpty()) operation.consecutiveEmptyRuns + 1 else 0
    operation.durationSeconds = Duration
      .between(started, now)
      .toMillis()
      .coerceAtLeast(0)
      .toDouble() / 1000
    operation.lastCompletion = now
  }

  private fun snapshot(
    operation: CollectionOperation,
    items: List<CollectionItem>,
  ): CollectionSnapshot {
    val active = items.filter { it.active }
    return CollectionSnapshot(
      key = operation.key,
      initializedAt = operation.initializedAt,
      expected = active.mapTo(linkedSetOf()) { it.symbol },
      attempted = operation.attempted,
      fetched = operation.fetched,
      persisted = operation.persisted,
      failed = operation.failed,
      lastAttempt = operation.lastAttempt,
      lastCompletion = operation.lastCompletion,
      lastPersistence = operation.lastPersistence,
      lastFullSuccess = operation.lastFullSuccess,
      consecutiveEmptyRuns = operation.consecutiveEmptyRuns,
      durationSeconds = operation.durationSeconds,
      itemSuccesses = active.associate { it.symbol to it.lastSuccess },
      itemInitializedAt = active.associate { it.symbol to it.initializedAt },
    )
  }

  private fun later(
    previous: Instant?,
    current: Instant,
  ): Instant = if (previous == null || current.isAfter(previous)) current else previous
}
