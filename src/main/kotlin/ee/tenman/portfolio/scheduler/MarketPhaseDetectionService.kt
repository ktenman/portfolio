package ee.tenman.portfolio.scheduler

import org.springframework.stereotype.Service
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@Service
class MarketPhaseDetectionService(
  private val clock: Clock = Clock.systemDefaultZone(),
) {
  private val nyseZone = ZoneId.of("America/New_York")

  fun detectMarketPhase(timestamp: Instant = Instant.now(clock)): MarketPhase {
    val nyseTime = ZonedDateTime.ofInstant(timestamp, nyseZone)

    if (isWeekend(nyseTime)) {
      return MarketPhase.WEEKEND
    }

    val localTime = nyseTime.toLocalTime()

    return when {
      isMainMarketHours(localTime) -> MarketPhase.MAIN_MARKET_HOURS
      isPrePostMarketHours(localTime) -> MarketPhase.PRE_POST_MARKET
      else -> MarketPhase.OFF_HOURS
    }
  }

  private fun isWeekend(dateTime: ZonedDateTime): Boolean =
    dateTime.dayOfWeek == DayOfWeek.SATURDAY || dateTime.dayOfWeek == DayOfWeek.SUNDAY

  private fun isMainMarketHours(time: LocalTime): Boolean {
    val marketOpen = LocalTime.of(10, 30)
    val marketClose = LocalTime.of(17, 30)
    return !time.isBefore(marketOpen) && time.isBefore(marketClose)
  }

  private fun isPrePostMarketHours(time: LocalTime): Boolean {
    val preMarketStart = LocalTime.of(4, 0)
    val preMarketEnd = LocalTime.of(10, 30)
    val afterMarketStart = LocalTime.of(17, 30)
    val afterMarketEnd = LocalTime.of(20, 0)

    return (!time.isBefore(preMarketStart) && time.isBefore(preMarketEnd)) ||
      (!time.isBefore(afterMarketStart) && time.isBefore(afterMarketEnd))
  }
}
