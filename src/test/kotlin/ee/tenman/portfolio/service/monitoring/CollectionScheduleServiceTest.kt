package ee.tenman.portfolio.service.monitoring

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.CollectionMonitoringProperties
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.model.CollectionSnapshot
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class CollectionScheduleServiceTest {
  @Test
  fun `should disable expectations when scheduling is disabled`() {
    val clock = clock("2026-09-25T10:00:00Z")
    val service = service(clock, false)
    val snapshot = snapshot(CollectionKey.BINANCE_PRICES)
    expect(service.expectation(snapshot).enabled).toEqual(false)
  }

  @Test
  fun `should distinguish an empty configuration from a failed collection`() {
    val clock = clock("2026-09-25T10:00:00Z")
    val service = service(clock)
    val snapshot = snapshot(CollectionKey.BINANCE_PRICES).copy(expected = emptySet())
    expect(service.expectation(snapshot).expectedNow).toEqual(false)
  }

  @Test
  fun `should respect the four minute Lightyear startup delay`() {
    val clock = clock("2026-09-25T10:00:00Z")
    val service = service(clock)
    every { clock.instant() } returns Instant.parse("2026-09-25T10:03:59Z")
    expect(service.expectation(snapshot(CollectionKey.LIGHTYEAR_PRICES)).expectedNow).toEqual(false)
  }

  @Test
  fun `should expect Lightyear prices after startup grace`() {
    val clock = clock("2026-09-25T10:00:00Z")
    val service = service(clock)
    every { clock.instant() } returns Instant.parse("2026-09-25T10:04:00Z")
    expect(service.expectation(snapshot(CollectionKey.LIGHTYEAR_PRICES)).expectedNow).toEqual(true)
  }

  @Test
  fun `should stop expecting Lightyear prices on weekends`() {
    val clock = clock("2026-09-25T10:00:00Z")
    val service = service(clock)
    every { clock.instant() } returns Instant.parse("2026-09-26T10:00:00Z")
    expect(service.expectation(snapshot(CollectionKey.LIGHTYEAR_PRICES)).expectedNow).toEqual(false)
  }

  @Test
  fun `should stop expecting Lightyear prices overnight`() {
    val clock = clock("2026-09-25T10:00:00Z")
    val service = service(clock)
    every { clock.instant() } returns Instant.parse("2026-09-27T22:30:00Z")
    expect(service.expectation(snapshot(CollectionKey.LIGHTYEAR_PRICES)).expectedNow).toEqual(false)
  }

  @Test
  fun `should start a fresh collection window on Monday morning`() {
    val clock = clock("2026-09-25T10:00:00Z")
    val service = service(clock)
    every { clock.instant() } returns Instant.parse("2026-09-28T03:00:00Z")
    expect(service.expectation(snapshot(CollectionKey.LIGHTYEAR_PRICES)).windowStart)
      .toEqual(Instant.parse("2026-09-28T03:00:00Z"))
  }

  @Test
  fun `should preserve a missed completion deadline while a new run is hung`() {
    val clock = clock("2026-09-25T10:00:00Z")
    val service = service(clock)
    val snapshot =
      snapshot(CollectionKey.BINANCE_PRICES).copy(
      lastCompletion = Instant.parse("2026-09-25T09:50:00Z"),
      lastAttempt = Instant.parse("2026-09-25T10:00:00Z"),
    )
    every { clock.instant() } returns Instant.parse("2026-09-25T10:10:00Z")
    expect(service.expectation(snapshot).deadline).toEqual(Instant.parse("2026-09-25T09:54:00Z"))
  }

  @Test
  fun `should preserve collection age across application restarts`() {
    val clock = clock("2026-09-25T10:00:00Z")
    val service = service(clock)
    every { clock.instant() } returns Instant.parse("2026-09-25T10:02:00Z")
    expect(service.expectation(snapshot(CollectionKey.BINANCE_PRICES)).windowStart)
      .toEqual(Instant.parse("2026-09-24T00:00:00Z"))
  }

  @Test
  fun `should give a previously empty price provider time to collect newly configured items`() {
    val clock = clock("2026-09-25T10:00:00Z")
    val service = service(clock)
    val configured = Instant.parse("2026-09-25T10:05:00Z")
    val snapshot = snapshot(CollectionKey.BINANCE_PRICES).copy(itemInitializedAt = mapOf("VGLA:GER:EUR" to configured))
    expect(service.expectation(snapshot).windowStart).toEqual(configured)
  }

  @Test
  fun `should wait for the next cron when a long empty provider receives its first instrument`() {
    val clock = clock("2026-09-25T10:00:00Z")
    val service = service(clock)
    val configured = Instant.parse("2026-09-25T10:05:00Z")
    every { clock.instant() } returns Instant.parse("2026-09-25T10:36:00Z")
    val snapshot = snapshot(CollectionKey.FT_HISTORY).copy(itemInitializedAt = mapOf("VGLA:GER:EUR" to configured))
    expect(service.expectation(snapshot).deadline).toEqual(Instant.parse("2026-09-26T02:30:00Z"))
  }

  @Test
  fun `should wait for the next cron for an item added to a successful daily provider`() {
    val clock = clock("2026-09-25T00:00:00Z")
    val service = service(clock)
    val success = Instant.parse("2026-09-25T02:05:00Z")
    val snapshot =
      snapshot(CollectionKey.FT_HISTORY).copy(
      expected = setOf("VGLA:GER:EUR", "NEW:GER:EUR"),
      lastFullSuccess = success,
      itemSuccesses = mapOf("VGLA:GER:EUR" to success, "NEW:GER:EUR" to null),
      itemInitializedAt =
        mapOf(
        "VGLA:GER:EUR" to Instant.parse("2026-09-24T00:00:00Z"),
        "NEW:GER:EUR" to Instant.parse("2026-09-25T10:05:00Z"),
      ),
        )
    every { clock.instant() } returns Instant.parse("2026-09-25T10:36:00Z")
    expect(service.expectation(snapshot).deadline).toEqual(Instant.parse("2026-09-26T02:30:00Z"))
  }

  @Test
  fun `should preserve an overdue initial deadline when another daily item is added`() {
    val clock = clock("2026-09-24T00:00:00Z")
    val service = service(clock)
    val snapshot =
      snapshot(CollectionKey.FT_HISTORY).copy(
      expected = setOf("VGLA:GER:EUR", "NEW:GER:EUR"),
      itemSuccesses = mapOf("VGLA:GER:EUR" to null, "NEW:GER:EUR" to null),
      itemInitializedAt =
        mapOf(
        "VGLA:GER:EUR" to Instant.parse("2026-09-24T00:00:00Z"),
        "NEW:GER:EUR" to Instant.parse("2026-09-25T10:05:00Z"),
      ),
        )
    every { clock.instant() } returns Instant.parse("2026-09-25T10:36:00Z")
    expect(service.expectation(snapshot).deadline).toEqual(Instant.parse("2026-09-24T00:30:10Z"))
  }

  @Test
  fun `should preserve a missed cron deadline when another daily item is added`() {
    val clock = clock("2026-09-24T00:00:00Z")
    val service = service(clock)
    val success = Instant.parse("2026-09-24T02:05:00Z")
    val snapshot =
      snapshot(CollectionKey.FT_HISTORY).copy(
      expected = setOf("VGLA:GER:EUR", "NEW:GER:EUR"),
      lastFullSuccess = success,
      itemSuccesses = mapOf("VGLA:GER:EUR" to success, "NEW:GER:EUR" to null),
      itemInitializedAt =
        mapOf(
        "VGLA:GER:EUR" to Instant.parse("2026-09-24T00:00:00Z"),
        "NEW:GER:EUR" to Instant.parse("2026-09-25T10:05:00Z"),
      ),
        )
    every { clock.instant() } returns Instant.parse("2026-09-25T10:36:00Z")
    expect(service.expectation(snapshot).deadline).toEqual(Instant.parse("2026-09-25T02:30:00Z"))
  }

  @Test
  fun `should preserve the next cron deadline for a later configured daily item across restarts`() {
    val clock = clock("2026-09-25T11:00:00Z")
    val service = service(clock)
    val snapshot =
      snapshot(CollectionKey.FT_HISTORY).copy(
      itemInitializedAt = mapOf("VGLA:GER:EUR" to Instant.parse("2026-09-25T10:05:00Z")),
    )
    every { clock.instant() } returns Instant.parse("2026-09-25T11:05:00Z")
    expect(service.expectation(snapshot).deadline).toEqual(Instant.parse("2026-09-26T02:30:00Z"))
  }

  @Test
  fun `should preserve an overdue initial daily deadline across restarts`() {
    val clock = clock("2026-09-25T11:00:00Z")
    val service = service(clock)
    val snapshot =
      snapshot(CollectionKey.FT_HISTORY).copy(
      itemInitializedAt = mapOf("VGLA:GER:EUR" to Instant.parse("2026-09-24T00:00:00Z")),
    )
    every { clock.instant() } returns Instant.parse("2026-09-25T11:05:00Z")
    expect(service.expectation(snapshot).deadline).toEqual(Instant.parse("2026-09-24T00:30:10Z"))
  }

  @Test
  fun `should follow daylight saving for the first cron of a later configured daily item`() {
    val clock = clock("2026-10-23T10:00:00Z")
    val service = service(clock)
    val snapshot =
      snapshot(CollectionKey.FT_HISTORY).copy(
      itemInitializedAt = mapOf("VGLA:GER:EUR" to Instant.parse("2026-10-24T10:05:00Z")),
    )
    every { clock.instant() } returns Instant.parse("2026-10-24T11:00:00Z")
    expect(service.expectation(snapshot).deadline).toEqual(Instant.parse("2026-10-25T03:30:00Z"))
  }

  @Test
  fun `should use the daily schedule after a successful initial history collection`() {
    val clock = clock("2026-09-25T10:00:00Z")
    val service = service(clock)
    val snapshot = snapshot(CollectionKey.FT_HISTORY).copy(lastFullSuccess = Instant.parse("2026-09-24T10:00:00Z"))
    expect(service.expectation(snapshot).deadline).toEqual(Instant.parse("2026-09-25T02:30:00Z"))
  }

  @Test
  fun `should not hide a missed daily update behind a partial completion`() {
    val clock = clock("2026-09-25T10:00:00Z")
    val service = service(clock)
    val snapshot =
      snapshot(CollectionKey.LIGHTYEAR_HISTORY).copy(
      lastFullSuccess = Instant.parse("2026-09-24T03:00:00Z"),
      lastCompletion = Instant.parse("2026-09-25T03:00:00Z"),
    )
    expect(service.expectation(snapshot).deadline).toEqual(Instant.parse("2026-09-25T03:00:00Z"))
  }

  @Test
  fun `should detect a daily job which has never completed after its initial grace`() {
    val clock = clock("2026-09-25T10:00:00Z")
    val service = service(clock)
    val snapshot = snapshot(CollectionKey.VANGUARD_HOLDINGS).copy(initializedAt = Instant.parse("2026-09-25T10:00:00Z"))
    expect(service.expectation(snapshot).deadline).toEqual(Instant.parse("2026-09-25T10:31:00Z"))
  }

  @Test
  fun `should follow the daily schedule through the daylight saving transition`() {
    val clock = clock("2026-10-24T10:00:00Z")
    val service = service(clock)
    val snapshot = snapshot(CollectionKey.FT_HISTORY).copy(lastFullSuccess = Instant.parse("2026-10-24T02:01:00Z"))
    expect(service.expectation(snapshot).deadline).toEqual(Instant.parse("2026-10-25T03:30:00Z"))
  }

  private fun service(
    clock: Clock,
    enabled: Boolean = true,
  ): CollectionScheduleService {
    val started = clock.instant().toEpochMilli()
    val context = mockk<ApplicationContext> { every { startupDate } returns started }
    return CollectionScheduleService(clock, CollectionMonitoringProperties(), enabled, 120000, 60000, context)
  }

  private fun clock(value: String): Clock =
    mockk {
    every { instant() } returns Instant.parse(value)
    every { zone } returns ZoneId.of("Europe/Tallinn")
  }

  private fun snapshot(key: CollectionKey): CollectionSnapshot =
    CollectionSnapshot(
    key = key,
    initializedAt = Instant.parse("2026-09-24T00:00:00Z"),
    expected = setOf("VGLA:GER:EUR"),
    attempted = 0,
    fetched = 0,
    persisted = 0,
    failed = 0,
    lastAttempt = null,
    lastCompletion = null,
    lastPersistence = null,
    lastFullSuccess = null,
    consecutiveEmptyRuns = 0,
    durationSeconds = 0.0,
    itemSuccesses = mapOf("VGLA:GER:EUR" to null),
  )
}
