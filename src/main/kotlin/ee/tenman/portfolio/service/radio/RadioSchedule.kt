package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.RadioAiring
import java.nio.file.Files
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val PLAN_FILE = "plan.json"
const val HEARTBEAT_FILE = "heartbeat.json"
val MISSED_FOR: Duration = Duration.ofDays(1)
private val GRACE = Duration.ofMinutes(2)
val LAG: Duration = Duration.ofSeconds(20)
private val PADDING = Duration.ofSeconds(10)
private val JOIN = Duration.ofMinutes(10)
private val UNSAFE = Regex("[^0-9a-z]+")
private val MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

data class RadioProgrammeDto(
  val id: Long,
  val name: String,
  val description: String?,
  val recordEvery: Boolean,
  val slots: List<AirSlot>,
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
  val slot: Slot,
)

data class RadioStation(
  val onAir: StationAiring?,
  val next: StationAiring?,
  val capture: Capture?,
  val freeBytes: Long?,
  val hours: Long?,
  val recorder: RecorderState,
  val last: LastRecording?,
  val format: AudioFormat?,
)

enum class RecorderState { WAITING, RECORDING, OFF }

data class LastRecording(
  val programme: String,
  val end: Long,
)

data class Heartbeat(
  val until: Double,
)

enum class MissedReason(
  val code: Double,
) {
  NEVER_RAN(1.0),
  DOWN(2.0),
  SAVED_NOTHING(3.0),
}

data class MissedCapture(
  val capture: Capture,
  val reason: MissedReason,
)

fun recorder(
  heartbeat: Heartbeat?,
  recording: Boolean,
  now: Instant,
): RecorderState =
  when {
    !alive(heartbeat, now) -> RecorderState.OFF
    recording -> RecorderState.RECORDING
    else -> RecorderState.WAITING
  }

fun recorded(root: Path): Set<String> {
  val names = Files.list(root).use { files -> files.map { it.fileName.toString() }.toList() }
  return names.toSet() + read<List<String>>(root.resolve(DELETED_FILE)).orEmpty()
}

fun missed(
  captures: List<Capture>,
  recorded: Set<String>,
  heartbeat: Heartbeat?,
  now: Instant,
): List<MissedCapture> {
  val reason =
    when {
      heartbeat == null -> MissedReason.NEVER_RAN
      !alive(heartbeat, now) -> MissedReason.DOWN
      else -> MissedReason.SAVED_NOTHING
    }
  val window = (now - MISSED_FOR).epochSecond..now.epochSecond
  return captures.filter { it.end in window && it.name !in recorded }.map { MissedCapture(it, reason) }
}

private fun alive(
  heartbeat: Heartbeat?,
  now: Instant,
): Boolean = heartbeat != null && now.epochSecond <= heartbeat.until + GRACE.seconds

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
        last == null || airing.start > last.maxOf { it.end } + JOIN -> groups + listOf(listOf(airing))
        else -> groups.dropLast(1) + listOf(last + airing)
      }
    }.map { capture(it, zone) }

private fun capture(
  group: List<PlannedAiring>,
  zone: ZoneId,
): Capture {
  val slugs = group.map { safe(it.slug) }.distinct()
  val start = group.first().start
  val end = group.maxOf { it.end }
  return Capture(
    name = slugs.joinToString("-", prefix = "${MINUTE.format(start.atZone(zone))}-"),
    programmes = group.map { it.programme }.distinct(),
    start = (start + LAG - PADDING).epochSecond,
    end = (end + LAG + PADDING).epochSecond,
    slot = Slot(start.epochSecond, end.epochSecond),
  )
}

fun List<Capture>.cover(airing: RadioAiring): Boolean =
  any { it.slot.start <= airing.startsAt.epochSecond && it.slot.end >= airing.endsAt.epochSecond }

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
