package ee.tenman.portfolio.service.common

import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class EasterHolidayService {
  fun getEasterHolidayDates(year: Int): Set<LocalDate> {
    val easter = easterSunday(year)
    return setOf(easter.minusDays(2), easter.plusDays(1))
  }

  private fun easterSunday(year: Int): LocalDate {
    val cycle = year % 19
    val century = year / 100
    val yearOfCentury = year % 100
    val centurySkippedLeaps = century / 4
    val centuryRemainder = century % 4
    val lunarShift = (century + 8) / 25
    val lunarCorrection = (century - lunarShift + 1) / 3
    val epact = (19 * cycle + century - centurySkippedLeaps - lunarCorrection + 15) % 30
    val leaps = yearOfCentury / 4
    val remainder = yearOfCentury % 4
    val weekdayOffset = (32 + 2 * centuryRemainder + 2 * leaps - epact - remainder) % 7
    val correction = (cycle + 11 * epact + 22 * weekdayOffset) / 451
    val dayNumber = epact + weekdayOffset - 7 * correction + 114
    return LocalDate.of(year, dayNumber / 31, dayNumber % 31 + 1)
  }
}
