package ee.tenman.portfolio.service.monitoring

import ee.tenman.portfolio.configuration.CollectionMonitoringProperties
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.model.CollectionExpectation
import ee.tenman.portfolio.model.CollectionSchedules
import ee.tenman.portfolio.model.CollectionSnapshot
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

@Service
class CollectionScheduleService(
  private val clock: Clock,
  private val properties: CollectionMonitoringProperties,
  @param:Value("\${scheduling.enabled:true}") private val enabled: Boolean,
  @param:Value("\${scheduling.jobs.binance-interval:120000}") private val binanceInterval: Long,
  @param:Value("\${scheduling.jobs.trading212-interval:60000}") private val trading212Interval: Long,
  context: ApplicationContext,
) {
  private val started = Instant.ofEpochMilli(context.startupDate)

  fun expectation(snapshot: CollectionSnapshot): CollectionExpectation {
    val now = clock.instant()
    val delay = startup(snapshot.key)
    val warming = now.isBefore(started.plusSeconds(maxOf(delay, properties.startupGrace.seconds)))
    val window = window(snapshot, now)
    val expected = enabled && snapshot.expected.isNotEmpty() && !warming && active(snapshot.key, now)
    val deadline = if (snapshot.key.operation == "prices") priceDeadline(snapshot, window) else dailyDeadline(snapshot)
    return CollectionExpectation(enabled, expected, window, deadline)
  }

  private fun window(
    snapshot: CollectionSnapshot,
    now: Instant,
  ): Instant {
    val initial = configuredAt(snapshot).plusSeconds(startup(snapshot.key))
    if (snapshot.key != CollectionKey.LIGHTYEAR_PRICES) return initial
    val opening =
      now
      .atZone(ZONE)
      .toLocalDate()
      .atTime(6, 0)
      .atZone(ZONE)
      .toInstant()
    return maxOf(initial, opening)
  }

  private fun active(
    key: CollectionKey,
    now: Instant,
  ): Boolean {
    if (key != CollectionKey.LIGHTYEAR_PRICES) return true
    val local = now.atZone(ZONE)
    return local.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) && local.hour >= 6
  }

  private fun priceDeadline(
    snapshot: CollectionSnapshot,
    window: Instant,
  ): Instant {
    val interval =
      when (snapshot.key) {
      CollectionKey.LIGHTYEAR_PRICES -> 30000L
      CollectionKey.TRADING212_PRICES -> trading212Interval
      else -> binanceInterval
    }
    val last = maxOf(snapshot.lastCompletion ?: window, window)
    return last.plusMillis(interval).plus(properties.priceGrace)
  }

  private fun dailyDeadline(snapshot: CollectionSnapshot): Instant {
    val success = snapshot.lastFullSuccess ?: return initialDeadline(snapshot)
    if (configuredAt(snapshot).isAfter(success)) return initialDeadline(snapshot)
    val configured =
      snapshot.itemInitializedAt
      .filter { (symbol, _) -> snapshot.itemSuccesses[symbol] == null }
      .values
        .minOrNull()
      ?.let { initialDeadline(snapshot, it) }
    val scheduled = scheduledDeadline(snapshot.key, success)
    return configured?.let { minOf(it, scheduled) } ?: scheduled
  }

  private fun initialDeadline(
    snapshot: CollectionSnapshot,
    configured: Instant = configuredAt(snapshot),
  ): Instant =
    if (configured.isAfter(snapshot.initializedAt)) {
      scheduledDeadline(snapshot.key, configured)
    } else {
      snapshot.initializedAt.plusSeconds(startup(snapshot.key)).plus(properties.dailyGrace)
    }

  private fun scheduledDeadline(
    key: CollectionKey,
    after: Instant,
  ): Instant = requireNotNull(CRONS.getValue(key).next(after.atZone(ZONE))).toInstant().plus(properties.dailyGrace)

  private fun configuredAt(snapshot: CollectionSnapshot): Instant =
    maxOf(snapshot.initializedAt, snapshot.itemInitializedAt.values.minOrNull() ?: snapshot.initializedAt)

  private fun startup(key: CollectionKey): Long =
    when (key) {
    CollectionKey.LIGHTYEAR_PRICES -> CollectionSchedules.LIGHTYEAR_PRICE_STARTUP_SECONDS
    CollectionKey.TRADING212_PRICES -> CollectionSchedules.TRADING212_PRICE_STARTUP_SECONDS
    CollectionKey.BINANCE_PRICES -> 0L
    CollectionKey.FT_HISTORY -> CollectionSchedules.FT_HISTORY_STARTUP_SECONDS
    CollectionKey.LIGHTYEAR_HISTORY -> CollectionSchedules.LIGHTYEAR_HISTORY_STARTUP_SECONDS
    CollectionKey.LIGHTYEAR_HOLDINGS, CollectionKey.TRADING212_HOLDINGS -> CollectionSchedules.HOLDINGS_STARTUP_SECONDS
    CollectionKey.BLACKROCK_HOLDINGS -> CollectionSchedules.BLACKROCK_HOLDINGS_STARTUP_SECONDS
    CollectionKey.VANGUARD_HOLDINGS -> CollectionSchedules.VANGUARD_HOLDINGS_STARTUP_SECONDS
  }

  companion object {
    private val ZONE = ZoneId.of(CollectionSchedules.TIME_ZONE)
    private val CRONS =
      mapOf(
      CollectionKey.FT_HISTORY to CollectionSchedules.FT_HISTORY_CRON,
      CollectionKey.LIGHTYEAR_HISTORY to CollectionSchedules.LIGHTYEAR_HISTORY_CRON,
      CollectionKey.LIGHTYEAR_HOLDINGS to CollectionSchedules.LIGHTYEAR_HOLDINGS_CRON,
      CollectionKey.TRADING212_HOLDINGS to CollectionSchedules.TRADING212_HOLDINGS_CRON,
      CollectionKey.BLACKROCK_HOLDINGS to CollectionSchedules.BLACKROCK_HOLDINGS_CRON,
      CollectionKey.VANGUARD_HOLDINGS to CollectionSchedules.VANGUARD_HOLDINGS_CRON,
    ).mapValues { CronExpression.parse(it.value) }
  }
}
