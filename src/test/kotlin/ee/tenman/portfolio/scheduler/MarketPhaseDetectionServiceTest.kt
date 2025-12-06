package ee.tenman.portfolio.scheduler

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
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

  @ParameterizedTest
  @CsvSource(
    "2025, 1, 1, New Year Day 2025",
    "2025, 4, 18, Good Friday 2025",
    "2025, 4, 21, Easter Monday 2025",
    "2025, 5, 1, Labour Day 2025",
    "2025, 12, 24, Christmas Eve 2025",
    "2025, 12, 25, Christmas Day 2025",
    "2025, 12, 26, Boxing Day 2025",
    "2025, 12, 31, New Year Eve 2025",
    "2026, 1, 1, New Year Day 2026",
    "2026, 4, 3, Good Friday 2026",
    "2026, 4, 6, Easter Monday 2026",
    "2026, 5, 1, Labour Day 2026",
    "2026, 12, 24, Christmas Eve 2026",
    "2026, 12, 25, Christmas Day 2026",
    "2026, 12, 26, Boxing Day 2026",
    "2026, 12, 31, New Year Eve 2026",
    "2027, 1, 1, New Year Day 2027",
    "2027, 3, 26, Good Friday 2027",
    "2027, 3, 29, Easter Monday 2027",
    "2027, 5, 1, Labour Day 2027",
    "2027, 12, 24, Christmas Eve 2027",
    "2027, 12, 25, Christmas Day 2027",
    "2027, 12, 26, Boxing Day 2027",
    "2027, 12, 31, New Year Eve 2027",
  )
  fun `should detect WEEKEND on Xetra holidays`(
    year: Int,
    month: Int,
    day: Int,
    @Suppress("UNUSED_PARAMETER") _holidayName: String,
  ) {
    val holiday = createNyTime(year, month, day, 12, 0)

    val result = service.detectMarketPhase(holiday)

    expect(result).toEqual(MarketPhase.WEEKEND)
  }

  @Test
  fun `should detect MAIN_MARKET_HOURS on regular weekday not a Xetra holiday`() {
    val regularDay = createNyTime(2025, 3, 17, 12, 0)

    val result = service.detectMarketPhase(regularDay)

    expect(result).toEqual(MarketPhase.MAIN_MARKET_HOURS)
  }

  @Test
  fun `isWeekendPhase should return true on Saturday`() {
    val saturday = createNyTime(2024, 1, 13, 12, 0)
    val serviceWithSaturday = MarketPhaseDetectionService(Clock.fixed(saturday, nyZone))

    val result = serviceWithSaturday.isWeekendPhase()

    expect(result).toEqual(true)
  }

  @Test
  fun `isWeekendPhase should return true on Xetra holiday`() {
    val christmasDay = createNyTime(2025, 12, 25, 12, 0)
    val serviceWithHoliday = MarketPhaseDetectionService(Clock.fixed(christmasDay, nyZone))

    val result = serviceWithHoliday.isWeekendPhase()

    expect(result).toEqual(true)
  }

  @Test
  fun `isWeekendPhase should return false on weekday`() {
    val weekday = createNyTime(2024, 1, 15, 12, 0)
    val serviceWithWeekday = MarketPhaseDetectionService(Clock.fixed(weekday, nyZone))

    val result = serviceWithWeekday.isWeekendPhase()

    expect(result).toEqual(false)
  }

  @ParameterizedTest
  @CsvSource(
    "2028, 4, 14, Good Friday 2028",
    "2028, 4, 17, Easter Monday 2028",
    "2029, 3, 30, Good Friday 2029",
    "2029, 4, 2, Easter Monday 2029",
    "2030, 4, 19, Good Friday 2030",
    "2030, 4, 22, Easter Monday 2030",
    "2031, 4, 11, Good Friday 2031",
    "2031, 4, 14, Easter Monday 2031",
    "2032, 3, 26, Good Friday 2032",
    "2032, 3, 29, Easter Monday 2032",
    "2033, 4, 15, Good Friday 2033",
    "2033, 4, 18, Easter Monday 2033",
    "2034, 4, 7, Good Friday 2034",
    "2034, 4, 10, Easter Monday 2034",
    "2035, 3, 23, Good Friday 2035",
    "2035, 3, 26, Easter Monday 2035",
  )
  fun `should detect WEEKEND on Easter holidays in future years via JollyDay`(
    year: Int,
    month: Int,
    day: Int,
    @Suppress("UNUSED_PARAMETER") _holidayName: String,
  ) {
    val holiday = createNyTime(year, month, day, 12, 0)

    val result = service.detectMarketPhase(holiday)

    expect(result).toEqual(MarketPhase.WEEKEND)
  }

  @ParameterizedTest
  @CsvSource(
    "2025, 5, 29, Ascension Day 2025",
    "2025, 6, 9, Whit Monday 2025",
    "2025, 10, 3, German Unity Day 2025",
    "2026, 5, 14, Ascension Day 2026",
    "2026, 5, 25, Whit Monday 2026",
    "2028, 10, 3, German Unity Day 2028",
  )
  fun `should detect MAIN_MARKET_HOURS on German holidays where XETRA is open`(
    year: Int,
    month: Int,
    day: Int,
    @Suppress("UNUSED_PARAMETER") _holidayName: String,
  ) {
    val holiday = createNyTime(year, month, day, 12, 0)

    val result = service.detectMarketPhase(holiday)

    expect(result).toEqual(MarketPhase.MAIN_MARKET_HOURS)
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
