package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.common.percentOf
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PriceSnapshot
import ee.tenman.portfolio.domain.TimeRange
import ee.tenman.portfolio.model.PriceChange
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@Service
class PriceChangeService(
  private val dailyPriceService: DailyPriceService,
  private val priceSnapshotService: PriceSnapshotService,
  private val clock: Clock,
) {
  companion object {
    private val MAX_SNAPSHOT_AGE: Duration = Duration.ofHours(6)
    private const val PERCENT_SCALE = 10
    private const val PREVIOUS_PRICE_LOOKBACK_DAYS = 5L
  }

  @Transactional(readOnly = true)
  fun getPriceChanges(
    instruments: Collection<Instrument>,
    period: TimeRange,
    priceLookup: PriceLookup,
  ): Map<Long, PriceChange> {
    val fromSnapshots = if (period == TimeRange.ONE_DAY) getPriceChangesFromSnapshots(instruments) else emptyMap()
    val currentDate = LocalDate.now(clock)
    val targetDate = period.startDate(currentDate) ?: return fromSnapshots
    val remaining = instruments.filterNot { fromSnapshots.containsKey(it.id) }
    return getPriceChangesFromDailyPrices(remaining, currentDate, targetDate, priceLookup) + fromSnapshots
  }

  private fun getPriceChangesFromSnapshots(instruments: Collection<Instrument>): Map<Long, PriceChange> {
    val now = Instant.now(clock)
    val targetHour = now.minus(Duration.ofHours(24))
    val snapshots =
      priceSnapshotService
        .findAllBetween(instruments.map { it.id }, targetHour.minus(MAX_SNAPSHOT_AGE), now)
        .groupBy { it.instrument.id }
    return instruments
      .mapNotNull { instrument ->
        val own = snapshots[instrument.id].orEmpty().filter { it.providerName == instrument.providerName }
        val current = own.closestAtOrBefore(now) ?: return@mapNotNull null
        val previous = own.closestAtOrBefore(targetHour) ?: return@mapNotNull null
        instrument.id to change(current.price, previous.price)
      }.toMap()
  }

  private fun List<PriceSnapshot>.closestAtOrBefore(referenceTime: Instant): PriceSnapshot? {
    val earliest = referenceTime.minus(MAX_SNAPSHOT_AGE)
    return filter { it.snapshotHour in earliest..referenceTime }.maxByOrNull { it.snapshotHour }
  }

  private fun getPriceChangesFromDailyPrices(
    instruments: Collection<Instrument>,
    currentDate: LocalDate,
    targetDate: LocalDate,
    priceLookup: PriceLookup,
  ): Map<Long, PriceChange> {
    if (instruments.isEmpty()) return emptyMap()
    val previousPrices = dailyPriceService.findLatestPrices(instruments, targetDate, targetDate.minusDays(PREVIOUS_PRICE_LOOKBACK_DAYS))
    return instruments
      .mapNotNull { instrument ->
        val currentPrice = priceLookup.priceOnOrBefore(instrument.id, currentDate) ?: return@mapNotNull null
        val previousPrice = previousPrices[instrument.id] ?: return@mapNotNull null
        instrument.id to change(currentPrice, previousPrice)
      }.toMap()
  }

  private fun change(
    currentPrice: BigDecimal,
    previousPrice: BigDecimal,
  ): PriceChange {
    val changeAmount = currentPrice.subtract(previousPrice)
    return PriceChange(changeAmount, calculateChangePercent(changeAmount, previousPrice))
  }

  private fun calculateChangePercent(
    changeAmount: BigDecimal,
    previousPrice: BigDecimal,
  ): Double {
    if (previousPrice.compareTo(BigDecimal.ZERO) == 0) return 0.0
    return changeAmount.percentOf(previousPrice, PERCENT_SCALE).toDouble()
  }
}
