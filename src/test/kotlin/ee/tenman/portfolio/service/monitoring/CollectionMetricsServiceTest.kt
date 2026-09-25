package ee.tenman.portfolio.service.monitoring

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.CollectionMonitoringProperties
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.model.CollectionSnapshot
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class CollectionMetricsServiceTest {
  @Test
  fun `should publish all monitored operations before the first collection`() {
    val fixture = fixture()
    expect(
      fixture.registry
      .find("portfolio.collection.operation.info")
      .gauges()
      .size,
        ).toEqual(9)
  }

  @Test
  fun `should publish zero success for a configured instrument that never collected`() {
    val fixture = fixture()
    fixture.metrics.refresh()
    expect(
      fixture.registry
        .find("portfolio.collection.item.last.success.timestamp.seconds")
      .tags("provider", "lightyear", "operation", "prices", "instrument", "VGLA:GER:EUR")
        .gauge()
        ?.value(),
        ).toEqual(0.0)
  }

  @Test
  fun `should keep inventory unhealthy until durable state can be loaded`() {
    val fixture = fixture()
    expect(
      fixture.registry
      .find("portfolio.collection.inventory.ready")
      .gauge()
      ?.value(),
        ).toEqual(0.0)
  }

  @Test
  fun `should mark a database read failure as missing monitoring data`() {
    val fixture = fixture()
    fixture.metrics.refresh()
    every { fixture.state.snapshots() } throws IllegalStateException("Unavailable")
    fixture.metrics.refresh()
    expect(
      fixture.registry
      .find("portfolio.collection.inventory.ready")
      .gauge()
      ?.value(),
        ).toEqual(0.0)
  }

  @Test
  fun `should stop trusting the metrics cache if its refresh task stops`() {
    val fixture = fixture()
    fixture.metrics.refresh()
    every { fixture.clock.instant() } returns NOW.plusSeconds(61)
    expect(
      fixture.registry
      .find("portfolio.collection.inventory.ready")
      .gauge()
      ?.value(),
        ).toEqual(0.0)
  }

  @Test
  fun `should remove obsolete instrument meters after configuration changes`() {
    val fixture = fixture()
    fixture.metrics.refresh()
    every { fixture.inventory.configured() } returns inventory(emptySet())
    every { fixture.state.snapshots() } returns snapshots(emptySet())
    fixture.metrics.refresh()
    expect(
      fixture.registry
      .find("portfolio.collection.item.last.success.timestamp.seconds")
      .gauges()
      .size,
        ).toEqual(0)
  }

  @Test
  fun `should expose open job circuit breakers without creating new breakers`() {
    val fixture = fixture()
    fixture.breakers.circuitBreaker("job-execution:LightyearPriceRetrievalJob").transitionToOpenState()
    expect(
      fixture.registry
        .find("portfolio.collection.circuit.breaker.state")
      .tags("provider", "lightyear", "operation", "prices")
        .gauge()
        ?.value(),
        ).toEqual(1.0)
  }

  @Test
  fun `should reject an incomplete inventory snapshot as missing monitoring data`() {
    val fixture = fixture()
    every { fixture.state.snapshots() } returns snapshots().drop(1)
    fixture.metrics.refresh()
    expect(
      fixture.registry
      .find("portfolio.collection.inventory.ready")
      .gauge()
      ?.value(),
        ).toEqual(0.0)
  }

  @Test
  fun `should trust a complete freshly loaded inventory`() {
    val fixture = fixture()
    fixture.metrics.refresh()
    expect(
      fixture.registry
      .find("portfolio.collection.inventory.ready")
      .gauge()
      ?.value(),
        ).toEqual(1.0)
  }

  private fun fixture(): MetricsFixture {
    val clock =
      mockk<Clock> {
      every { instant() } returns NOW
      every { zone } returns ZoneId.of("Europe/Tallinn")
    }
    val inventory = mockk<CollectionInventoryService> { every { configured() } returns inventory() }
    val initial = snapshots()
    val state =
      mockk<CollectionStateService> {
      every { initialize(any(), any()) } answers { initial.first { it.key == firstArg<CollectionKey>() } }
      every { snapshots() } returns initial
    }
    val registry = SimpleMeterRegistry()
    val breakers = CircuitBreakerRegistry.ofDefaults()
    val context = mockk<ApplicationContext> { every { startupDate } returns NOW.toEpochMilli() }
    val schedules = CollectionScheduleService(clock, CollectionMonitoringProperties(), true, 120000, 60000, context)
    val metrics = CollectionMetricsService(inventory, state, schedules, breakers, clock)
    metrics.bindTo(registry)
    return MetricsFixture(clock, inventory, state, registry, breakers, metrics)
  }

  private fun inventory(symbols: Set<String> = setOf("VGLA:GER:EUR")): Map<CollectionKey, Set<String>> =
    CollectionKey.entries.associateWith { if (it == CollectionKey.LIGHTYEAR_PRICES) symbols else emptySet() }

  private fun snapshots(symbols: Set<String> = setOf("VGLA:GER:EUR")): List<CollectionSnapshot> =
    CollectionKey.entries.map { key ->
      val items = if (key == CollectionKey.LIGHTYEAR_PRICES) symbols else emptySet()
      CollectionSnapshot(
        key,
        NOW,
        items,
        0,
        0,
        0,
        0,
        null,
        null,
        null,
        null,
        0,
        0.0,
        items.associateWith { null },
        items.associateWith { NOW },
      )
    }

  companion object {
    private val NOW = Instant.parse("2026-09-25T10:00:00Z")
  }
}

private data class MetricsFixture(
  val clock: Clock,
  val inventory: CollectionInventoryService,
  val state: CollectionStateService,
  val registry: SimpleMeterRegistry,
  val breakers: CircuitBreakerRegistry,
  val metrics: CollectionMetricsService,
)
