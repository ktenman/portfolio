package ee.tenman.portfolio.scheduler

import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class MarketPhaseDetectionServiceTest {
  private val nyZone = ZoneId.of("America/New_York")
  private val service = MarketPhaseDetectionService(Clock.fixed(Instant.EPOCH, nyZone))

  @Test
  fun `should detect MAIN_MARKET_HOURS during trading hours`() {
    val marketOpenTime = createNyTime(2024, 1, 15, 12, 0)

    val result = service.detectMarketPhase(marketOpenTime)

    expect(result).toEqual(MarketPhase.MAIN_MARKET_HOURS)
  }

  @Test
  fun `should detect MAIN_MARKET_HOURS at exact market open`() {
    val marketOpenTime = createNyTime(2024, 1, 15, 10, 30)

    val result = service.detectMarketPhase(marketOpenTime)

    expect(result).toEqual(MarketPhase.MAIN_MARKET_HOURS)
  }

  @Test
  fun `should detect MAIN_MARKET_HOURS just before market close`() {
    val beforeClose = createNyTime(2024, 1, 15, 17, 29)

    val result = service.detectMarketPhase(beforeClose)

    expect(result).toEqual(MarketPhase.MAIN_MARKET_HOURS)
  }

  @Test
  fun `should detect PRE_POST_MARKET during pre-market hours`() {
    val preMarketTime = createNyTime(2024, 1, 15, 8, 0)

    val result = service.detectMarketPhase(preMarketTime)

    expect(result).toEqual(MarketPhase.PRE_POST_MARKET)
  }

  @Test
  fun `should detect PRE_POST_MARKET during after-hours`() {
    val afterHoursTime = createNyTime(2024, 1, 15, 18, 0)

    val result = service.detectMarketPhase(afterHoursTime)

    expect(result).toEqual(MarketPhase.PRE_POST_MARKET)
  }

  @Test
  fun `should detect PRE_POST_MARKET at exact pre-market start`() {
    val preMarketStart = createNyTime(2024, 1, 15, 4, 0)

    val result = service.detectMarketPhase(preMarketStart)

    expect(result).toEqual(MarketPhase.PRE_POST_MARKET)
  }

  @Test
  fun `should detect OFF_HOURS during night time`() {
    val nightTime = createNyTime(2024, 1, 15, 2, 0)

    val result = service.detectMarketPhase(nightTime)

    expect(result).toEqual(MarketPhase.OFF_HOURS)
  }

  @Test
  fun `should detect OFF_HOURS late evening`() {
    val lateEvening = createNyTime(2024, 1, 15, 22, 0)

    val result = service.detectMarketPhase(lateEvening)

    expect(result).toEqual(MarketPhase.OFF_HOURS)
  }

  @Test
  fun `should detect WEEKEND on Saturday`() {
    val saturday = createNyTime(2024, 1, 13, 10, 0)

    val result = service.detectMarketPhase(saturday)

    expect(result).toEqual(MarketPhase.WEEKEND)
    expect(ZonedDateTime.ofInstant(saturday, nyZone).dayOfWeek).toEqual(DayOfWeek.SATURDAY)
  }

  @Test
  fun `should detect WEEKEND on Sunday`() {
    val sunday = createNyTime(2024, 1, 14, 15, 0)

    val result = service.detectMarketPhase(sunday)

    expect(result).toEqual(MarketPhase.WEEKEND)
    expect(ZonedDateTime.ofInstant(sunday, nyZone).dayOfWeek).toEqual(DayOfWeek.SUNDAY)
  }

  @Test
  fun `should transition from PRE_POST_MARKET to MAIN_MARKET_HOURS at 10-30 AM`() {
    val justBefore = createNyTime(2024, 1, 15, 10, 29)
    val justAfter = createNyTime(2024, 1, 15, 10, 30)

    expect(service.detectMarketPhase(justBefore)).toEqual(MarketPhase.PRE_POST_MARKET)
    expect(service.detectMarketPhase(justAfter)).toEqual(MarketPhase.MAIN_MARKET_HOURS)
  }

  @Test
  fun `should transition from MAIN_MARKET_HOURS to PRE_POST_MARKET at 5-30 PM`() {
    val justBefore = createNyTime(2024, 1, 15, 17, 29)
    val atClose = createNyTime(2024, 1, 15, 17, 30)

    expect(service.detectMarketPhase(justBefore)).toEqual(MarketPhase.MAIN_MARKET_HOURS)
    expect(service.detectMarketPhase(atClose)).toEqual(MarketPhase.PRE_POST_MARKET)
  }

  @Test
  fun `should transition from PRE_POST_MARKET to OFF_HOURS at 8 PM`() {
    val justBefore = createNyTime(2024, 1, 15, 19, 59)
    val atEightPm = createNyTime(2024, 1, 15, 20, 0)

    expect(service.detectMarketPhase(justBefore)).toEqual(MarketPhase.PRE_POST_MARKET)
    expect(service.detectMarketPhase(atEightPm)).toEqual(MarketPhase.OFF_HOURS)
  }

  private fun createNyTime(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
  ): Instant {
    val localDate = LocalDate.of(year, month, day)
    val localTime = LocalTime.of(hour, minute)
    return ZonedDateTime.of(localDate, localTime, nyZone).toInstant()
  }
}
