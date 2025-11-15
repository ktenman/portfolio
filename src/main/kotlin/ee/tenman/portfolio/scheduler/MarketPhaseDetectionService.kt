package ee.tenman.portfolio.scheduler

import org.springframework.stereotype.Service
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@Service
class MarketPhaseDetectionService(
  private val clock: Clock = Clock.systemDefaultZone(),
) {
  private val nyseZone = ZoneId.of("America/New_York")

  private val xetraHolidays =
    setOf(
    LocalDate.of(2025, 1, 1),
    LocalDate.of(2025, 4, 18),
    LocalDate.of(2025, 4, 21),
    LocalDate.of(2025, 5, 1),
    LocalDate.of(2025, 12, 24),
    LocalDate.of(2025, 12, 25),
    LocalDate.of(2025, 12, 26),
    LocalDate.of(2025, 12, 31),
    LocalDate.of(2026, 1, 1),
    LocalDate.of(2026, 4, 3),
    LocalDate.of(2026, 4, 6),
    LocalDate.of(2026, 5, 1),
    LocalDate.of(2026, 12, 24),
    LocalDate.of(2026, 12, 25),
    LocalDate.of(2026, 12, 26),
    LocalDate.of(2026, 12, 31),
    LocalDate.of(2027, 1, 1),
    LocalDate.of(2027, 3, 26),
    LocalDate.of(2027, 3, 29),
    LocalDate.of(2027, 5, 1),
    LocalDate.of(2027, 12, 24),
    LocalDate.of(2027, 12, 25),
    LocalDate.of(2027, 12, 26),
    LocalDate.of(2027, 12, 31),
  )

  fun detectMarketPhase(timestamp: Instant = Instant.now(clock)): MarketPhase {
    val nyseTime = ZonedDateTime.ofInstant(timestamp, nyseZone)

    if (isWeekend(nyseTime) || isXetraHoliday(nyseTime.toLocalDate())) {
      return MarketPhase.WEEKEND
    }

    val localTime = nyseTime.toLocalTime()

    return when {
      isMainMarketHours(localTime) -> MarketPhase.MAIN_MARKET_HOURS
      isPrePostMarketHours(localTime) -> MarketPhase.PRE_POST_MARKET
      else -> MarketPhase.OFF_HOURS
    }
  }

  fun isWeekendPhase(): Boolean = detectMarketPhase() == MarketPhase.WEEKEND

  private fun isWeekend(dateTime: ZonedDateTime): Boolean =
    dateTime.dayOfWeek == DayOfWeek.SATURDAY || dateTime.dayOfWeek == DayOfWeek.SUNDAY

  private fun isXetraHoliday(date: LocalDate): Boolean = xetraHolidays.contains(date)

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
