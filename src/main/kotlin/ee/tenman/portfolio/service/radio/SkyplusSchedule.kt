package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.skyplus.SkyplusRow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

fun skyplusAirings(
  rows: List<SkyplusRow>,
  from: LocalDate,
  days: Long,
  zone: ZoneId,
): List<ScheduledAiring> {
  val weekdays = rows.associateWith(::weekdays)
  return (0 until days).map(from::plusDays).flatMap { date ->
    rows.filter { date.dayOfWeek in weekdays.getValue(it) }.map { airing(it, date, zone) }
  }
}

private fun weekdays(row: SkyplusRow): Set<DayOfWeek> =
  row.days
    .map { day ->
    require(day in 1..7) { "Sky Plus schedule row ${row.id} lists day $day, not 1 to 7" }
    DayOfWeek.of(day)
  }.toSet()

private fun airing(
  row: SkyplusRow,
  date: LocalDate,
  zone: ZoneId,
): ScheduledAiring {
  val start = date.atTime(row.startTime).atZone(zone).toInstant()
  val ends = if (row.endTime > row.startTime) date else date.plusDays(1)
  val end = ends.atTime(row.endTime).atZone(zone).toInstant()
  val show = ScheduledShow(id("SKYPLUS/${row.id}"), row.name, safe(fold(row.name)), null)
  return ScheduledAiring(id("SKYPLUS/${row.id}/${start.epochSecond}"), show, row.name, start, end, null)
}
