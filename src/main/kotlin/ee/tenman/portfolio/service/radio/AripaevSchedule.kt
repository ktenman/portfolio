package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.configuration.JsonMapperFactory
import tools.jackson.databind.DeserializationFeature
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

private const val SCHEDULE = "\"schedule\":["

data class AripaevRow(
  val start: OffsetDateTime,
  val end: OffsetDateTime,
  val series: String,
  val seriesSlug: String? = null,
  val title: String? = null,
)

private data class AripaevShow(
  val row: AripaevRow,
  val slug: String,
  val title: String?,
) {
  fun day(zone: ZoneId): LocalDate = row.start.atZoneSameInstant(zone).toLocalDate()

  fun id(): Long = id("$slug-${row.start.toEpochSecond()}")
}

fun aripaevAirings(
  payload: String,
  zone: ZoneId,
): List<ScheduledAiring> {
  val shows =
    rows(payload)
      .sortedBy { it.start }
      .mapNotNull { row -> row.seriesSlug?.let { AripaevShow(row, it, row.title?.let(::clean)) } }
  check(shows.isNotEmpty()) { "Äripäev schedule lists no shows" }
  val originals = originals(shows, zone)
  return shows.map { show ->
    ScheduledAiring(
      show.id(),
      ScheduledShow(id(show.slug), show.row.series, show.slug, null),
      show.title ?: show.row.series,
      show.row.start.toInstant(),
      show.row.end.toInstant(),
      originals[show]?.id(),
    )
  }
}

private fun rows(payload: String): List<AripaevRow> {
  val index = payload.indexOf(SCHEDULE)
  check(index >= 0) { "Äripäev schedule payload of ${payload.length} characters holds no schedule array" }
  return JsonMapperFactory.instance
    .readerForListOf(AripaevRow::class.java)
    .without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    .readValue(payload.substring(index + SCHEDULE.length - 1))
}

private fun clean(title: String): String = title.trimEnd().removeSuffix("*").trimEnd()

private fun originals(
  shows: List<AripaevShow>,
  zone: ZoneId,
): Map<AripaevShow, AripaevShow> =
  shows.fold(emptyMap()) { found, show ->
    val earlier = shows.takeWhile { it !== show }.filter { it.slug == show.slug }
    source(show, earlier, found, zone)?.let { found + (show to (found[it] ?: it)) } ?: found
  }

private fun source(
  show: AripaevShow,
  earlier: List<AripaevShow>,
  found: Map<AripaevShow, AripaevShow>,
  zone: ZoneId,
): AripaevShow? {
  if (show.title != null) return earlier.firstOrNull { it.title == show.title }
  return earlier.firstOrNull { it.day(zone) == show.day(zone) }
    ?: earlier.lastOrNull { it.title == null && it !in found }.takeIf { show.day(zone).dayOfWeek >= DayOfWeek.SATURDAY }
}
