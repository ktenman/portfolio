package ee.tenman.portfolio.service

import de.focus_shift.jollyday.core.HolidayManager
import de.focus_shift.jollyday.core.ManagerParameters
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.EASTER_HOLIDAYS_CACHE
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.Year

@Service
class EasterHolidayService {
  private val holidayManager: HolidayManager = HolidayManager.getInstance(ManagerParameters.create("de"))

  @Cacheable(EASTER_HOLIDAYS_CACHE)
  fun getEasterHolidayDates(year: Int): Set<LocalDate> =
    holidayManager
      .getHolidays(Year.of(year))
      .filter { it.propertiesKey in setOf("christian.GOOD_FRIDAY", "christian.EASTER_MONDAY") }
      .map { it.date }
      .toSet()
}
