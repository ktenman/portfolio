package ee.tenman.portfolio.service.common

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class EasterHolidayServiceTest {
  private val service = EasterHolidayService()

  @ParameterizedTest
  @CsvSource(
    "1900, 1900-04-13, 1900-04-16",
    "1961, 1961-03-31, 1961-04-03",
    "2008, 2008-03-21, 2008-03-24",
    "2024, 2024-03-29, 2024-04-01",
    "2025, 2025-04-18, 2025-04-21",
    "2026, 2026-04-03, 2026-04-06",
    "2038, 2038-04-23, 2038-04-26",
    "2100, 2100-03-26, 2100-03-29",
  )
  fun `should return good friday and easter monday for the given year`(
    year: Int,
    goodFriday: LocalDate,
    easterMonday: LocalDate,
  ) {
    expect(service.getEasterHolidayDates(year)).toEqual(setOf(goodFriday, easterMonday))
  }
}
