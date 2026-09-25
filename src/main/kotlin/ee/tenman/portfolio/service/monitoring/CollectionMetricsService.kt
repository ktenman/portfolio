package ee.tenman.portfolio.service.monitoring

import ee.tenman.portfolio.domain.CollectionKey
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
      configured.forEach { (key, symbols) -> state.initialize(key, symbols) }
      val loaded = state.snapshots().associateBy { it.key }
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
    private val JOBS =
      mapOf(
      CollectionKey.LIGHTYEAR_PRICES to "LightyearPriceRetrievalJob",
      CollectionKey.TRADING212_PRICES to "Trading212DataRetrievalJob",
      CollectionKey.BINANCE_PRICES to "BinanceDataRetrievalJob",
      CollectionKey.FT_HISTORY to "FtDataRetrievalJob",
      CollectionKey.LIGHTYEAR_HISTORY to "LightyearHistoricalDataRetrievalJob",
      CollectionKey.LIGHTYEAR_HOLDINGS to "LightyearDataFetchJob",
      CollectionKey.TRADING212_HOLDINGS to "Trading212HoldingsRetrievalJob",
      CollectionKey.BLACKROCK_HOLDINGS to "CsusHoldingsRetrievalJob",
      CollectionKey.VANGUARD_HOLDINGS to "VanguardHoldingsRetrievalJob",
    )
  }
}
