package ee.tenman.portfolio.service.radio

import java.time.LocalDate
import java.time.LocalTime

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
  val now: Long,
  val recorded: List<RecordedSpan>,
  val seconds: Long,
  val day: List<DayBlock>,
  val night: List<DayBlock>,
)

data class RecordedSpan(
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
)

fun RecordingSummary.take(): Take = Take(name, programme, status.startedAt.toLong(), (status.startedAt + status.seconds).toLong())

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
    .filter { it.end > from && it.start < until }
    .map { RecordedSpan(maxOf(it.start, from), minOf(it.end, until)) }
    .sortedBy { it.start }
