package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.notToContain
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.skyplus.SkyplusRow
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SkyplusScheduleTest {
  private val tallinn = ZoneId.of("Europe/Tallinn")

  private fun row(
    id: Long,
    name: String,
    days: List<Int>,
    start: String,
    end: String,
  ) = SkyplusRow(id, name, days, LocalTime.parse(start), LocalTime.parse(end))

  private fun week(vararg rows: SkyplusRow) = skyplusAirings(rows.toList(), LocalDate.of(2026, 11, 16), 7, tallinn)

  @Test
  fun `should give a Monday-to-Thursday row no Friday airing`() {
    val airings = week(row(20, "Õhtu Plus", listOf(1, 2, 3, 4), "18:00", "22:00"))
    expect(airings.map { it.start.atZone(tallinn).dayOfWeek }).notToContain(DayOfWeek.FRIDAY)
  }

  @Test
  fun `should return the same ids on a second expansion`() {
    val row = row(4, "Õhtune Äratus", listOf(1, 2, 3, 4, 5), "15:00", "18:00")
    expect(week(row).map { it.externalId }).toEqual(week(row).map { it.externalId })
  }

  @Test
  fun `should keep six in the morning local time on both sides of the clock change of 28 March 2027`() {
    val row = row(2, "Hommikuprogramm", listOf(6, 7), "06:00", "10:00")
    val airings = skyplusAirings(listOf(row), LocalDate.of(2027, 3, 27), 2, tallinn)
    expect(airings.map { it.start.atZone(tallinn).toLocalTime() }).toContainExactly(LocalTime.of(6, 0), LocalTime.of(6, 0))
  }

  @Test
  fun `should end a row that ends at midnight on the next day`() {
    val airing = week(row(9, "Öine Plus", listOf(1), "22:00", "00:00")).single()
    expect(Duration.between(airing.start, airing.end)).toEqual(Duration.ofHours(2))
  }

  @Test
  fun `should fail on day 0`() {
    expect { week(row(9, "Öine Plus", listOf(0), "22:00", "23:00")) }
      .toThrow<IllegalArgumentException>()
      .messageToContain("9", "0")
  }

  @Test
  fun `should keep the programme id of a renamed row`() {
    val before = week(row(3, "Päeva Plus", listOf(1), "10:00", "15:00")).single()
    val after = week(row(3, "Päevane Plus", listOf(1), "10:00", "15:00")).single()
    expect(after.show.externalId).toEqual(before.show.externalId)
  }

  @Test
  fun `should give a programme a slug without diacritics`() {
    expect(week(row(4, "Õhtune Äratus", listOf(1), "15:00", "18:00")).single().show.slug).toEqual("ohtune-aratus")
  }

  @Test
  fun `should keep the ids of Sky Plus apart from the ids of another row`() {
    val airings = week(row(2, "Hommikuprogramm", listOf(1), "06:00", "10:00"), row(3, "Päeva Plus", listOf(1), "10:00", "15:00"))
    expect(airings.map { it.show.externalId }.distinct().size).toEqual(2)
  }

  @Test
  fun `should expand the template of 11 November into 19 airings and 76 hours for 16 to 22 November`() {
    val airings =
      week(
        row(2, "Hommikuprogramm", listOf(1, 2, 3, 4, 5), "06:00", "10:00"),
        row(3, "Päeva Plus", listOf(1, 2, 3, 4, 5), "10:00", "15:00"),
        row(4, "Õhtune Äratus", listOf(1, 2, 3, 4, 5), "15:00", "18:00"),
        row(20, "Õhtu Plus", listOf(1, 2, 3, 4), "18:00", "22:00"),
      )
    expect(airings.size to airings.sumOf { Duration.between(it.start, it.end).toHours() }).toEqual(19 to 76L)
  }
}
