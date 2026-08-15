package ee.tenman.portfolio.domain

import com.fasterxml.jackson.annotation.JsonValue
import java.time.LocalDate
import kotlin.math.roundToInt

enum class TimeRange(
  @get:JsonValue val code: String,
) {
  ONE_DAY("1D"),
  TWO_DAYS("2D"),
  THREE_DAYS("3D"),
  ONE_WEEK("1W"),
  ONE_MONTH("1M"),
  THREE_MONTHS("3M"),
  SIX_MONTHS("6M"),
  YTD("YTD"),
  ONE_YEAR("1Y"),
  TWO_YEARS("2Y"),
  THREE_YEARS("3Y"),
  FOUR_YEARS("4Y"),
  FIVE_YEARS("5Y"),
  MAX("MAX"),
  ;

  fun startDate(today: LocalDate): LocalDate? =
    when (this) {
      ONE_DAY -> today.minusDays(1)
      TWO_DAYS -> today.minusDays(2)
      THREE_DAYS -> today.minusDays(3)
      ONE_WEEK -> today.minusWeeks(1)
      ONE_MONTH -> today.minusMonths(1)
      THREE_MONTHS -> today.minusMonths(3)
      SIX_MONTHS -> today.minusMonths(6)
      YTD -> today.withDayOfYear(1)
      ONE_YEAR -> today.minusYears(1)
      TWO_YEARS -> today.minusYears(2)
      THREE_YEARS -> today.minusYears(3)
      FOUR_YEARS -> today.minusYears(4)
      FIVE_YEARS -> today.minusYears(5)
      MAX -> null
    }

  fun dates(
    firstDate: LocalDate,
    today: LocalDate,
  ): List<LocalDate> {
    val end = today.minusDays(1)
    val start = startDate(today)?.coerceAtLeast(firstDate) ?: firstDate
    if (start.isAfter(end)) return emptyList()
    return sample(start.datesUntil(end.plusDays(1)).toList())
  }

  companion object {
    const val MAX_POINTS = 60

    fun from(code: String): TimeRange =
      entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown summary range $code")

    fun <T> sample(
      items: List<T>,
      maxPoints: Int = MAX_POINTS,
    ): List<T> {
      if (items.size <= maxPoints) return items
      val step = (items.size - 1).toDouble() / (maxPoints - 1)
      return (0 until maxPoints).map { items[(it * step).roundToInt()] }
    }
  }
}
