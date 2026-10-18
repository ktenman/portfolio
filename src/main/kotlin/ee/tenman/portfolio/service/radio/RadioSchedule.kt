package ee.tenman.portfolio.service.radio

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val PLAN_FILE = "plan.json"
private val PADDING = Duration.ofMinutes(5)
private val UNSAFE = Regex("[^0-9a-z]+")
private val MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

data class RadioProgrammeDto(
  val id: Long,
  val name: String,
  val description: String?,
  val recordEvery: Boolean,
  val slots: List<AirSlot>,
  val repeats: List<AirSlot>,
  val recordings: Int,
)

data class AirSlot(
  val days: List<DayOfWeek>,
  val start: LocalTime,
  val end: LocalTime,
)

data class ProgrammeChoice(
  val recordEvery: Boolean,
)

data class PlannedAiring(
  val programme: String,
  val slug: String,
  val start: Instant,
  val end: Instant,
)

data class Capture(
  val name: String,
  val programmes: List<String>,
  val start: Long,
  val end: Long,
)

data class RadioStation(
  val onAir: StationAiring?,
  val next: StationAiring?,
  val capture: Capture?,
  val freeBytes: Long,
  val hours: Long,
)

data class StationAiring(
  val name: String,
  val start: Long,
  val end: Long,
  val recorded: Boolean,
)

fun captures(
  airings: List<PlannedAiring>,
  zone: ZoneId,
): List<Capture> =
  airings
    .sortedBy { it.start }
    .fold(emptyList<List<PlannedAiring>>()) { groups, airing ->
      val last = groups.lastOrNull()
      when {
        last == null || airing.start - PADDING > last.maxOf { it.end } + PADDING -> groups + listOf(listOf(airing))
        else -> groups.dropLast(1) + listOf(last + airing)
      }
    }.map { capture(it, zone) }

private fun capture(
  group: List<PlannedAiring>,
  zone: ZoneId,
): Capture {
  val slugs = group.map { safe(it.slug) }.distinct()
  return Capture(
    name = slugs.joinToString("-", prefix = "${MINUTE.format(group.first().start.atZone(zone))}-"),
    programmes = group.map { it.programme }.distinct(),
    start = (group.first().start - PADDING).epochSecond,
    end = (group.maxOf { it.end } + PADDING).epochSecond,
  )
}

private fun safe(slug: String): String = slug.lowercase().replace(UNSAFE, "-").trim('-')

fun slots(
  times: List<Pair<Instant, Instant>>,
  zone: ZoneId,
): List<AirSlot> =
  times
    .map { (start, end) -> start.atZone(zone) to end.atZone(zone) }
    .groupBy({ (start, end) -> start.toLocalTime() to end.toLocalTime() }, { it.first.dayOfWeek })
    .map { (time, days) -> AirSlot(days.distinct().sorted(), time.first, time.second) }
    .sortedWith(compareBy({ it.days.first() }, { it.start }))
