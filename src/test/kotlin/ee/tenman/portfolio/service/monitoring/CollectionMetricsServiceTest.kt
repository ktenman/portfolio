package ee.tenman.portfolio.service.monitoring

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.CollectionMonitoringProperties
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.dto.CollectionStatus
import ee.tenman.portfolio.model.CollectionSnapshot
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

  @Test
  fun `should reinitialize only the collection whose configured instruments changed`() {
    val fixture = fixture()
    every { fixture.inventory.configured() } returns inventory(setOf("VGLA:GER:EUR", "ŽALG:VSE:EUR"))
    fixture.metrics.refresh()
    verify(exactly = 1) { fixture.state.initialize(any(), any()) }
  }

  @Test
  fun `should report every collection as ok before any scheduled deadline passes`() {
    val fixture = fixture()
    fixture.metrics.refresh()
    expect(
      fixture.metrics
      .collections()
      .map { it.status }
      .toSet(),
        ).toEqual(setOf(CollectionStatus.OK))
  }

  @Test
  fun `should report an open job circuit breaker in the collection status`() {
    val fixture = fixture()
    fixture.metrics.refresh()
    fixture.breakers.circuitBreaker("job-execution:LightyearPriceRetrievalJob").transitionToOpenState()
    expect(
      fixture.metrics
      .collections()
      .first()
      .status,
        ).toEqual(CollectionStatus.BREAKER_OPEN)
  }

  @Test
  fun `should report a collection with failed items as a partial failure`() {
    val fixture = fixture()
    every { fixture.state.snapshots() } returns snapshots().map { if (it.key == CollectionKey.FT_HISTORY) it.copy(failed = 2) else it }
    fixture.metrics.refresh()
    expect(
      fixture.metrics
      .collections()
      .first { it.provider == "ft" }
      .status,
        ).toEqual(CollectionStatus.PARTIAL_FAILURE)
  }

  @Test
  fun `should name the items not persisted during the last completed run`() {
    val fixture = fixture()
    val snapshot =
      snapshots(setOf("ÄRI:TLN:EUR", "VGLA:GER:EUR"))
      .first()
      .copy(
        failed = 1,
        lastAttempt = NOW.plusSeconds(30),
        lastCompletion = NOW.plusSeconds(10),
        durationSeconds = 10.0,
        itemSuccesses = mapOf("ÄRI:TLN:EUR" to NOW.minusSeconds(60), "VGLA:GER:EUR" to NOW.plusSeconds(5)),
      )
    every { fixture.inventory.configured() } returns inventory(snapshot.expected)
    every { fixture.state.snapshots() } returns listOf(snapshot) + snapshots().drop(1)
    fixture.metrics.refresh()
    expect(
      fixture.metrics
      .collections()
      .first()
      .failedItems,
        ).toEqual(listOf("ÄRI:TLN:EUR"))
  }

  @Test
  fun `should report a collection whose attempt is newer than its completion as running`() {
    val fixture = fixture()
    val snapshot = snapshots().first().copy(lastAttempt = NOW, lastCompletion = NOW.minusSeconds(60))
    every { fixture.state.snapshots() } returns listOf(snapshot) + snapshots().drop(1)
    fixture.metrics.refresh()
    expect(
      fixture.metrics
      .collections()
      .first()
      .status,
    ).toEqual(CollectionStatus.RUNNING)
  }

  @Test
  fun `should report an expected collection past its deadline as overdue`() {
    val fixture = fixture()
    fixture.metrics.refresh()
    every { fixture.clock.instant() } returns NOW.plusSeconds(3 * 86400)
    expect(
      fixture.metrics
      .collections()
      .first()
      .status,
        ).toEqual(CollectionStatus.OVERDUE)
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
