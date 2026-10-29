package ee.tenman.portfolio.service.radio

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

val DAY_STARTS: LocalTime = LocalTime.of(5, 0)
private const val SLACK = 60L
private val DATED = Regex("""(\s*\d{2}-\d{2}-\d{4})?\**$""")

enum class AiringState { RECORDED, PARTIAL, RECORDING, ON_AIR, PLANNED, ARCHIVED, OPEN, MISSED }

data class RadioDay(
  val date: LocalDate,
  val today: LocalDate,
  val days: List<LocalDate>,
  val from: Long,
  val until: Long,
  val ticks: List<DayTick>,
  val now: Long,
  val recorded: List<RecordedSpan>,
  val seconds: Long,
  val planned: List<PlannedSpan>,
  val day: List<DayBlock>,
  val night: List<DayBlock>,
)

data class DayTick(
  val at: Long,
  val midnight: Boolean,
)

data class RecordedSpan(
  val name: String,
  val programme: String,
  val start: Long,
  val end: Long,
  val recording: Boolean,
)

data class PlannedSpan(
  val programme: String,
  val start: Long,
  val end: Long,
)

data class DayBlock(
  val missed: Boolean,
  val airings: List<DayAiring>,
)

data class DayAiring(
  val id: Long,
  val name: String,
  val episode: String?,
  val start: Long,
  val end: Long,
  val state: AiringState,
  val once: Boolean,
  val repeat: Boolean,
  val original: OriginalAiring?,
  val recording: AiringRecording?,
)

data class OriginalAiring(
  val start: Long,
  val state: AiringState,
  val recording: AiringRecording?,
)

data class AiringRecording(
  val name: String,
  val programme: String,
  val start: Long,
  val end: Long,
  val from: Long,
  val to: Long,
)

data class AiringChoice(
  val record: Boolean,
)

data class Take(
  val name: String,
  val programme: String,
  val start: Long,
  val end: Long,
  val recording: Boolean,
  val slot: Slot?,
)

fun RecordingSummary.take(now: Long): Take {
  val recording = status.state == RecordingState.RECORDING
  val end = (status.startedAt + status.seconds).toLong()
  return Take(name, programme, status.startedAt.toLong(), if (recording) minOf(end, now) else end, recording, status.slot)
}

fun covered(
  start: Long,
  end: Long,
  takes: List<Take>,
): AiringRecording? =
  takes
    .map { AiringRecording(it.name, it.programme, it.start, it.end, maxOf(start, it.start), minOf(end, it.end)) }
    .filter { it.to - it.from >= SLACK }
    .maxByOrNull { it.to - it.from }

fun state(
  start: Long,
  end: Long,
  now: Long,
  recording: AiringRecording?,
  planned: Boolean,
  original: AiringState?,
): AiringState =
  when {
    now in start until end -> if (planned) AiringState.RECORDING else AiringState.ON_AIR
    recording != null && recording.to - recording.from >= end - start - SLACK -> AiringState.RECORDED
    recording != null -> AiringState.PARTIAL
    original == AiringState.RECORDED -> AiringState.ARCHIVED
    end <= now -> AiringState.MISSED
    planned -> AiringState.PLANNED
    else -> AiringState.OPEN
  }

fun episode(
  title: String,
  name: String,
): String? = title.replace(DATED, "").trim().takeUnless { it.isEmpty() || it.equals(name, ignoreCase = true) }

fun blocks(airings: List<DayAiring>): List<DayBlock> =
  airings.fold(emptyList()) { blocks, airing ->
    val missed = airing.state == AiringState.MISSED
    val last = blocks.lastOrNull()
    if (last != null && last.missed == missed) {
      blocks.dropLast(1) + last.copy(airings = last.airings + airing)
    } else {
      blocks + DayBlock(missed, listOf(airing))
    }
  }

fun spans(
  takes: List<Take>,
  from: Long,
  until: Long,
): List<RecordedSpan> =
  takes
    .map { it.drawn(from, until) }
    .filter { it.end > it.start }
    .sortedBy { it.start }

private fun Take.drawn(
  from: Long,
  until: Long,
) = RecordedSpan(
  name,
  programme,
  maxOf(start - LAG.seconds, slot?.start ?: from, from),
  minOf(end - LAG.seconds, slot?.end ?: until, until),
  recording,
)

fun planned(
  airings: List<DayAiring>,
  until: Long,
): List<PlannedSpan> =
  airings
    .filter { it.state == AiringState.PLANNED }
    .map { PlannedSpan(it.name, it.start, minOf(it.end, until)) }

fun ticks(
  date: LocalDate,
  zone: ZoneId,
): List<DayTick> =
  listOf(6, 12, 18).map { DayTick(date.atTime(it, 0).atZone(zone).toEpochSecond(), false) } +
    DayTick(date.plusDays(1).atStartOfDay(zone).toEpochSecond(), true)

fun week(today: LocalDate): List<LocalDate> {
  val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
  return (0L..6L).map(monday::plusDays)
}
