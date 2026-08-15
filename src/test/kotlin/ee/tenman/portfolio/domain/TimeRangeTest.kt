package ee.tenman.portfolio.domain

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TimeRangeTest {
  private val today = LocalDate.of(2026, 8, 14)

  @Test
  fun `should start one week before today for the one week range`() {
    expect(TimeRange.ONE_WEEK.startDate(today)).toEqual(LocalDate.of(2026, 8, 7))
  }

  @Test
  fun `should start yesterday for the one day range`() {
    expect(TimeRange.ONE_DAY.startDate(today)).toEqual(LocalDate.of(2026, 8, 13))
  }

  @Test
  fun `should start two days before today for the two days range`() {
    expect(TimeRange.TWO_DAYS.startDate(today)).toEqual(LocalDate.of(2026, 8, 12))
  }

  @Test
  fun `should start three days before today for the three days range`() {
    expect(TimeRange.THREE_DAYS.startDate(today)).toEqual(LocalDate.of(2026, 8, 11))
  }

  @Test
  fun `should start three calendar months before today for the three months range`() {
    expect(TimeRange.THREE_MONTHS.startDate(today)).toEqual(LocalDate.of(2026, 5, 14))
  }

  @Test
  fun `should start six calendar months before today for the six months range`() {
    expect(TimeRange.SIX_MONTHS.startDate(today)).toEqual(LocalDate.of(2026, 2, 14))
  }

  @Test
  fun `should start on the first day of the year for the year to date range`() {
    expect(TimeRange.YTD.startDate(today)).toEqual(LocalDate.of(2026, 1, 1))
  }

  @Test
  fun `should start on the first of January when today is the last day of the year`() {
    expect(TimeRange.YTD.startDate(LocalDate.of(2026, 12, 31))).toEqual(LocalDate.of(2026, 1, 1))
  }

  @Test
  fun `should start on the twenty eighth of February when a leap day is one year back`() {
    expect(TimeRange.ONE_YEAR.startDate(LocalDate.of(2025, 2, 28))).toEqual(LocalDate.of(2024, 2, 28))
  }

  @Test
  fun `should start four years back for the four year range`() {
    expect(TimeRange.FOUR_YEARS.startDate(today)).toEqual(LocalDate.of(2022, 8, 14))
  }

  @Test
  fun `should start five years back for the five year range`() {
    expect(TimeRange.FIVE_YEARS.startDate(today)).toEqual(LocalDate.of(2021, 8, 14))
  }

  @Test
  fun `should report an unbounded start for the max range`() {
    expect(TimeRange.MAX.startDate(today)).toEqual(null)
  }

  @Test
  fun `should produce seven consecutive dates ending yesterday for the one week range`() {
    expect(TimeRange.ONE_WEEK.dates(LocalDate.of(2020, 1, 2), today)).toContainExactly(
      LocalDate.of(2026, 8, 7),
      LocalDate.of(2026, 8, 8),
      LocalDate.of(2026, 8, 9),
      LocalDate.of(2026, 8, 10),
      LocalDate.of(2026, 8, 11),
      LocalDate.of(2026, 8, 12),
      LocalDate.of(2026, 8, 13),
    )
  }

  @Test
  fun `should start from the first transaction date when it falls inside the window`() {
    expect(TimeRange.ONE_WEEK.dates(LocalDate.of(2026, 8, 11), today)).toContainExactly(
      LocalDate.of(2026, 8, 11),
      LocalDate.of(2026, 8, 12),
      LocalDate.of(2026, 8, 13),
    )
  }

  @Test
  fun `should produce yesterday as the only date for the one day range`() {
    expect(TimeRange.ONE_DAY.dates(LocalDate.of(2020, 1, 2), today)).toContainExactly(LocalDate.of(2026, 8, 13))
  }

  @Test
  fun `should produce ninety two dates for the three months range`() {
    expect(TimeRange.THREE_MONTHS.dates(LocalDate.of(2020, 1, 2), today)).toHaveSize(92)
  }

  @Test
  fun `should produce one hundred and eighty one dates for the six months range`() {
    expect(TimeRange.SIX_MONTHS.dates(LocalDate.of(2020, 1, 2), today)).toHaveSize(181)
  }

  @Test
  fun `should cap the max range at the sampling limit`() {
    expect(TimeRange.MAX.dates(LocalDate.of(2020, 1, 2), today)).toHaveSize(366)
  }

  @Test
  fun `should keep both endpoints when sampling caps the max range`() {
    val dates = TimeRange.MAX.dates(LocalDate.of(2020, 1, 2), today)
    expect(dates.first() to dates.last()).toEqual(LocalDate.of(2020, 1, 2) to LocalDate.of(2026, 8, 13))
  }

  @Test
  fun `should return dates in ascending order for the max range`() {
    val dates = TimeRange.MAX.dates(LocalDate.of(2020, 1, 2), today)
    expect(dates).toEqual(dates.sorted())
  }

  @Test
  fun `should return no dates when the first transaction is after yesterday`() {
    expect(TimeRange.MAX.dates(today, today)).toHaveSize(0)
  }

  @Test
  fun `should return the list unchanged when it is at or under the sampling limit`() {
    expect(TimeRange.sample(listOf("ä", "ö", "ü"), 3)).toContainExactly("ä", "ö", "ü")
  }

  @Test
  fun `should keep the first and last element when sampling shrinks the list`() {
    expect(TimeRange.sample((1..100).toList(), 5)).toContainExactly(1, 26, 51, 75, 100)
  }

  @Test
  fun `should resolve a range from its lowercase code`() {
    expect(TimeRange.from("ytd")).toEqual(TimeRange.YTD)
  }

  @Test
  fun `should resolve a range from its uppercase code`() {
    expect(TimeRange.from("1Y")).toEqual(TimeRange.ONE_YEAR)
  }

  @Test
  fun `should throw when the code is unknown`() {
    expect { TimeRange.from("10Y") }.toThrow<IllegalArgumentException>()
  }

  @Test
  fun `should declare the ranges in chip order`() {
    expect(TimeRange.entries.map { it.code })
      .toContainExactly(
        "1D",
        "2D",
        "3D",
        "1W",
        "1M",
        "3M",
        "6M",
        "YTD",
        "1Y",
        "2Y",
        "3Y",
        "4Y",
        "5Y",
        "MAX",
      )
  }

  @Test
  fun `should keep every sampled index in bounds at the production limit`() {
    expect(TimeRange.sample((1..367).toList())).toHaveSize(366)
  }
}
