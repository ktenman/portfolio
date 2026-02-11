package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PriceChangePeriod
import ee.tenman.portfolio.domain.PriceSnapshot
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.model.PriceChange
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
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
  }

  @Transactional(readOnly = true)
  fun getPriceChange(
    instrument: Instrument,
    period: PriceChangePeriod = PriceChangePeriod.P24H,
  ): PriceChange? {
    if (period == PriceChangePeriod.P24H) {
      getPriceChangeFromSnapshots(instrument)?.let { return it }
    }
    return getPriceChangeFromDailyPrices(instrument, period)
  }

  private fun getPriceChangeFromSnapshots(instrument: Instrument): PriceChange? {
    val now = Instant.now(clock)
    val targetHour = now.minus(Duration.ofHours(24))
    val provider = instrument.providerName
    val currentSnapshot = findFreshSnapshot(instrument.id, provider, now) ?: return null
    val previousSnapshot = findFreshSnapshot(instrument.id, provider, targetHour) ?: return null
    val changeAmount = currentSnapshot.price.subtract(previousSnapshot.price)
    val changePercent = calculateChangePercent(changeAmount, previousSnapshot.price)
    return PriceChange(changeAmount, changePercent)
  }

  private fun findFreshSnapshot(
    instrumentId: Long,
    provider: ProviderName,
    referenceTime: Instant,
  ): PriceSnapshot? =
    priceSnapshotService.findClosestAtOrBefore(
      instrumentId,
      provider,
      referenceTime.minus(MAX_SNAPSHOT_AGE),
      referenceTime,
    )

  private fun getPriceChangeFromDailyPrices(
    instrument: Instrument,
    period: PriceChangePeriod,
  ): PriceChange? {
    val currentDate = LocalDate.now(clock)
    val targetDate = currentDate.minusDays(period.days.toLong())
    val currentPrice = dailyPriceService.findLastDailyPrice(instrument, currentDate)?.closePrice ?: return null
    val previousPrice =
      dailyPriceService
        .findPriceNear(instrument, targetDate)
        ?.closePrice ?: return null
    val changeAmount = currentPrice.subtract(previousPrice)
    val changePercent = calculateChangePercent(changeAmount, previousPrice)
    return PriceChange(changeAmount, changePercent)
  }

  private fun calculateChangePercent(
    changeAmount: BigDecimal,
    previousPrice: BigDecimal,
  ): Double {
    if (previousPrice.compareTo(BigDecimal.ZERO) == 0) return 0.0
    return changeAmount
      .divide(previousPrice, 10, RoundingMode.HALF_UP)
      .multiply(BigDecimal(100))
      .toDouble()
  }
}
