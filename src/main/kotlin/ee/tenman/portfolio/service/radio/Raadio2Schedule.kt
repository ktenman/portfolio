package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.err.ErrScheduleItem
import java.time.Instant

fun raadio2Airings(items: List<ErrScheduleItem>): List<ScheduledAiring> = items.map(::airing)

private fun airing(item: ErrScheduleItem): ScheduledAiring {
  val show = ScheduledShow(id("RAADIO2/${item.radiomanId}"), item.seriesName, safe(fold(item.seriesName)), null)
  val original = item.originalTime?.takeIf { item.isReplay }?.let { id("RAADIO2/${item.radiomanId}/$it") }
  val start = Instant.ofEpochSecond(item.startTime)
  val end = Instant.ofEpochSecond(item.endTime)
  return ScheduledAiring(id("RAADIO2/${item.radiomanId}/${item.startTime}"), show, item.heading ?: item.seriesName, start, end, original)
}
