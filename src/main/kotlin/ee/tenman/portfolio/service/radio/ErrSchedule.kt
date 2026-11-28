package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.err.ErrScheduleItem
import java.time.Instant

fun errAirings(
  station: Station,
  items: List<ErrScheduleItem>,
): List<ScheduledAiring> = items.map { airing(station, it) }

private fun airing(
  station: Station,
  item: ErrScheduleItem,
): ScheduledAiring {
  val series = "${station.name}/${item.radiomanId}"
  val name = item.programName.removeSuffix("*").trim()
  val show = ScheduledShow(id(series), name, safe(fold(name)), null)
  val original = item.originalTime?.takeIf { item.isReplay }?.let { id("$series/$it") }
  val start = Instant.ofEpochSecond(item.startTime)
  val end = Instant.ofEpochSecond(item.endTime)
  return ScheduledAiring(id("$series/${item.startTime}"), show, item.heading ?: name, start, end, original)
}
