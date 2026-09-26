package ee.tenman.portfolio.service.monitoring

import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.dto.CollectionStatus
import ee.tenman.portfolio.dto.CollectionStatusDto
import ee.tenman.portfolio.job.BinanceDataRetrievalJob
import ee.tenman.portfolio.job.CsusHoldingsRetrievalJob
import ee.tenman.portfolio.job.FtDataRetrievalJob
import ee.tenman.portfolio.job.LightyearDataFetchJob
import ee.tenman.portfolio.job.LightyearHistoricalDataRetrievalJob
import ee.tenman.portfolio.job.LightyearPriceRetrievalJob
import ee.tenman.portfolio.job.Trading212DataRetrievalJob
import ee.tenman.portfolio.job.Trading212HoldingsRetrievalJob
import ee.tenman.portfolio.job.VanguardHoldingsRetrievalJob
import ee.tenman.portfolio.model.CollectionExpectation
import ee.tenman.portfolio.model.CollectionSnapshot
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.binder.MeterBinder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Service
class CollectionMetricsService(
  private val inventory: CollectionInventoryService,
  private val state: CollectionStateService,
  private val schedules: CollectionScheduleService,
  private val breakers: CircuitBreakerRegistry,
  private val clock: Clock,
) : MeterBinder {
  private val log = LoggerFactory.getLogger(javaClass)
  private lateinit var registry: MeterRegistry
  private val items = mutableMapOf<Pair<CollectionKey, String>, List<Meter>>()

  @Volatile private var snapshots = emptyMap<CollectionKey, CollectionSnapshot>()

  @Volatile private var refreshed: Instant? = null

  override fun bindTo(registry: MeterRegistry) {
    this.registry = registry
    gauge("inventory.ready", Tags.empty()) { readiness() }
    CollectionKey.entries.forEach { register(it) }
  }

  @Synchronized
  fun refresh() {
    if (!::registry.isInitialized) return
    runCatching {
      val configured = inventory.configured()
      val loaded = synchronize(configured)
      check(loaded.keys == CollectionKey.entries.toSet()) { "Collection inventory is incomplete" }
      check(configured.all { (key, symbols) -> loaded.getValue(key).expected == symbols }) { "Collection inventory changed during refresh" }
      snapshots = loaded
      registerItems()
      refreshed = clock.instant()
    }.onFailure {
      refreshed = null
      log.warn("Collection monitoring refresh failed: ${it.javaClass.simpleName}")
    }
  }

  fun collections(): List<CollectionStatusDto> = snapshots.values.sortedBy { it.key.ordinal }.map { status(it) }

  private fun status(snapshot: CollectionSnapshot): CollectionStatusDto {
    val expectation = schedules.expectation(snapshot)
    val open = breaker(snapshot.key) == 1.0
    return CollectionStatusDto(
      key = snapshot.key.name,
      provider = snapshot.key.provider,
      operation = snapshot.key.operation,
      status = classify(snapshot, expectation, open),
      expected = snapshot.expected.size,
      fetched = snapshot.fetched,
      persisted = snapshot.persisted,
      failed = snapshot.failed,
      failedItems = failedItems(snapshot),
      consecutiveEmptyRuns = snapshot.consecutiveEmptyRuns,
      durationSeconds = snapshot.durationSeconds,
      lastAttempt = snapshot.lastAttempt,
      lastCompletion = snapshot.lastCompletion,
      lastFullSuccess = snapshot.lastFullSuccess,
      deadline = expectation.deadline,
      breakerOpen = open,
    )
  }

  private fun failedItems(snapshot: CollectionSnapshot): List<String> {
    if (snapshot.failed == 0) return emptyList()
    val completion = snapshot.lastCompletion ?: return emptyList()
    val start = completion.minusMillis((snapshot.durationSeconds * 1000).toLong())
    return snapshot.expected.filter { snapshot.itemSuccesses[it]?.isBefore(start) ?: true }.sorted()
  }

  private fun classify(
    snapshot: CollectionSnapshot,
    expectation: CollectionExpectation,
    open: Boolean,
  ): CollectionStatus =
    when {
      !expectation.enabled -> CollectionStatus.DISABLED
      open -> CollectionStatus.BREAKER_OPEN
      running(snapshot) -> CollectionStatus.RUNNING
      overdue(snapshot, expectation) -> CollectionStatus.OVERDUE
      snapshot.failed > 0 -> CollectionStatus.PARTIAL_FAILURE
      else -> CollectionStatus.OK
    }

  private fun running(snapshot: CollectionSnapshot): Boolean {
    val attempt = snapshot.lastAttempt ?: return false
    val completion = snapshot.lastCompletion ?: return true
    return attempt.isAfter(completion)
  }

  private fun overdue(
    snapshot: CollectionSnapshot,
    expectation: CollectionExpectation,
  ): Boolean = snapshot.expected.isNotEmpty() && expectation.expectedNow && clock.instant().isAfter(expectation.deadline)

  private fun synchronize(configured: Map<CollectionKey, Set<String>>): Map<CollectionKey, CollectionSnapshot> {
    val current = state.snapshots().associateBy { it.key }
    val drifted = configured.filter { (key, symbols) -> current[key]?.expected != symbols }
    if (drifted.isEmpty()) return current
    drifted.forEach { (key, symbols) -> state.initialize(key, symbols) }
    return state.snapshots().associateBy { it.key }
  }

  private fun readiness(): Double {
    val last = refreshed ?: return 0.0
    return if (Duration.between(last, clock.instant()).seconds <= 60) 1.0 else 0.0
  }

  private fun register(key: CollectionKey) {
    val tags = Tags.of("provider", key.provider, "operation", key.operation)
    gauge("operation.info", tags) { 1.0 }
    gauge("expected.items", tags) { snapshots[key]?.expected?.size?.toDouble() ?: 0.0 }
    gauge("enabled", tags) { expectation(key) { if (it.enabled) 1.0 else 0.0 } }
    gauge("expected.now", tags) { expectation(key) { if (it.expectedNow) 1.0 else 0.0 } }
    gauge("window.start.timestamp.seconds", tags) { expectation(key) { it.windowStart.epochSecond.toDouble() } }
    gauge("deadline.timestamp.seconds", tags) { expectation(key) { it.deadline.epochSecond.toDouble() } }
    gauge("circuit.breaker.state", tags) { breaker(key) }
    registerOutcomes(key, tags)
  }

  private fun registerOutcomes(
    key: CollectionKey,
    tags: Tags,
  ) {
    gauge("last.attempt.timestamp.seconds", tags) { seconds(snapshots[key]?.lastAttempt) }
    gauge("last.completion.timestamp.seconds", tags) { seconds(snapshots[key]?.lastCompletion) }
    gauge("last.persisted.timestamp.seconds", tags) { seconds(snapshots[key]?.lastPersistence) }
    gauge("last.full.success.timestamp.seconds", tags) { seconds(snapshots[key]?.lastFullSuccess) }
    gauge("last.run.attempted.items", tags) { snapshots[key]?.attempted?.toDouble() ?: 0.0 }
    gauge("last.run.fetched.items", tags) { snapshots[key]?.fetched?.toDouble() ?: 0.0 }
    gauge("last.run.persisted.items", tags) { snapshots[key]?.persisted?.toDouble() ?: 0.0 }
    gauge("last.run.failed.items", tags) { snapshots[key]?.failed?.toDouble() ?: 0.0 }
    gauge("consecutive.empty.runs", tags) { snapshots[key]?.consecutiveEmptyRuns?.toDouble() ?: 0.0 }
    gauge("run.duration.seconds", tags) { snapshots[key]?.durationSeconds ?: 0.0 }
  }

  private fun registerItems() {
    val configured = snapshots.flatMap { (key, snapshot) -> snapshot.expected.map { key to it } }.toSet()
    (items.keys - configured).forEach { item -> items.remove(item)?.forEach { registry.remove(it) } }
    (configured - items.keys).forEach { (key, symbol) ->
      val tags = Tags.of("provider", key.provider, "operation", key.operation, "instrument", symbol)
      items[key to symbol] =
        listOf(
        gauge("item.last.success.timestamp.seconds", tags) { seconds(snapshots[key]?.itemSuccesses?.get(symbol)) },
        gauge("item.initialized.timestamp.seconds", tags) { seconds(snapshots[key]?.itemInitializedAt?.get(symbol)) },
      )
    }
  }

  private fun expectation(
    key: CollectionKey,
    value: (CollectionExpectation) -> Double,
  ): Double = snapshots[key]?.let { value(schedules.expectation(it)) } ?: 0.0

  private fun breaker(key: CollectionKey): Double {
    val state = breakers.find("job-execution:${JOBS.getValue(key)}").map { it.state }.orElse(CircuitBreaker.State.CLOSED)
    return when (state) {
      CircuitBreaker.State.OPEN, CircuitBreaker.State.FORCED_OPEN -> 1.0
      CircuitBreaker.State.HALF_OPEN -> 2.0
      else -> 0.0
    }
  }

  private fun gauge(
    name: String,
    tags: Tags,
    value: () -> Double,
  ): Meter =
    Gauge
    .builder("portfolio.collection.$name", value, { it() })
    .tags(tags)
    .strongReference(true)
    .register(registry)

  private fun seconds(instant: Instant?): Double = instant?.epochSecond?.toDouble() ?: 0.0

  companion object {
    val JOBS =
      mapOf(
      CollectionKey.LIGHTYEAR_PRICES to LightyearPriceRetrievalJob::class.java.simpleName,
      CollectionKey.TRADING212_PRICES to Trading212DataRetrievalJob::class.java.simpleName,
      CollectionKey.BINANCE_PRICES to BinanceDataRetrievalJob::class.java.simpleName,
      CollectionKey.FT_HISTORY to FtDataRetrievalJob::class.java.simpleName,
      CollectionKey.LIGHTYEAR_HISTORY to LightyearHistoricalDataRetrievalJob::class.java.simpleName,
      CollectionKey.LIGHTYEAR_HOLDINGS to LightyearDataFetchJob::class.java.simpleName,
      CollectionKey.TRADING212_HOLDINGS to Trading212HoldingsRetrievalJob::class.java.simpleName,
      CollectionKey.BLACKROCK_HOLDINGS to CsusHoldingsRetrievalJob::class.java.simpleName,
      CollectionKey.VANGUARD_HOLDINGS to VanguardHoldingsRetrievalJob::class.java.simpleName,
    )
  }
}
