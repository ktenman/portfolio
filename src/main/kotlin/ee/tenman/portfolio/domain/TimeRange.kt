package ee.tenman.portfolio.domain

import com.fasterxml.jackson.annotation.JsonValue
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class TimeRange(
  @get:JsonValue val code: String,
) {
  ONE_DAY("1D"),
  TWO_DAYS("2D"),
  THREE_DAYS("3D"),
  FOUR_DAYS("4D"),
  FIVE_DAYS("5D"),
  SIX_DAYS("6D"),
  ONE_WEEK("1W"),
  ONE_MONTH("1M"),
  TWO_MONTHS("2M"),
  THREE_MONTHS("3M"),
  FOUR_MONTHS("4M"),
  FIVE_MONTHS("5M"),
  SIX_MONTHS("6M"),
  YTD("YTD"),
  ONE_YEAR("1Y"),
  TWO_YEARS("2Y"),
  THREE_YEARS("3Y"),
  FOUR_YEARS("4Y"),
  FIVE_YEARS("5Y"),
  SIX_YEARS("6Y"),
  MAX("MAX"),
  ;

  fun startDate(today: LocalDate): LocalDate? =
    when (this) {
      ONE_DAY -> today.minusDays(1)
      TWO_DAYS -> today.minusDays(2)
      THREE_DAYS -> today.minusDays(3)
      FOUR_DAYS -> today.minusDays(4)
      FIVE_DAYS -> today.minusDays(5)
      SIX_DAYS -> today.minusDays(6)
      ONE_WEEK -> today.minusWeeks(1)
      ONE_MONTH -> today.minusMonths(1)
      TWO_MONTHS -> today.minusMonths(2)
      THREE_MONTHS -> today.minusMonths(3)
      FOUR_MONTHS -> today.minusMonths(4)
      FIVE_MONTHS -> today.minusMonths(5)
      SIX_MONTHS -> today.minusMonths(6)
      YTD -> today.withDayOfYear(1)
      ONE_YEAR -> today.minusYears(1)
      TWO_YEARS -> today.minusYears(2)
      THREE_YEARS -> today.minusYears(3)
      FOUR_YEARS -> today.minusYears(4)
      FIVE_YEARS -> today.minusYears(5)
      SIX_YEARS -> today.minusYears(6)
      MAX -> null
    }

  fun dates(
    firstDate: LocalDate,
    today: LocalDate,
  ): List<LocalDate> {
    val end = today.minusDays(1)
    val start = startDate(today)?.coerceAtLeast(firstDate) ?: firstDate
    if (start.isAfter(end)) return emptyList()
    return start.datesUntil(end.plusDays(1)).toList()
  }

  fun intradayDays(today: LocalDate): Long? {
    val start = startDate(today) ?: return null
    val days = ChronoUnit.DAYS.between(start, today)
    return days.takeIf { it in 1..MAX_INTRADAY_DAYS }
  }

  companion object {
    const val DEFAULT_CODE = "1M"
    private const val MAX_INTRADAY_DAYS = 7L

    fun from(code: String): TimeRange =
      entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown summary range $code")
  }
}
