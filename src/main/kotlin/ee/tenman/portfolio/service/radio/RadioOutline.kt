package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.configuration.JsonMapperFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

private val TALLINN: ZoneId = ZoneId.of("Europe/Tallinn")
private val DAY = DateTimeFormatter.ofPattern("EEEE dd.MM.yyyy", Locale.ENGLISH)
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss")
private val AIR = DateTimeFormatter.ofPattern("HH:mm")
private const val MARGIN = 180.0

data class Slot(
  val start: Long,
  val end: Long,
)

data class Show(
  val name: String,
  val slot: Slot?,
  val from: Double,
  val to: Double,
) {
  val minutes: Int
    get() = round((to - from) / 60).toInt()
}

fun show(
  status: RecordingStatus,
  name: String,
  slot: Slot?,
): Show {
  if (slot == null || status.programmes.size < 2) return Show(name, slot, 0.0, status.seconds)
  val from = max(0.0, slot.start - status.startedAt - MARGIN)
  return Show(name, slot, from, min(status.seconds, slot.end - status.startedAt + MARGIN))
}

data class OutlineRead(
  val hosts: List<String>,
  val showStart: ShowStart,
  val breaks: List<OutlineBreak>,
  val showEnd: ShowEnd,
  val chapters: List<OutlineChapter>,
)

data class ShowStart(
  val lastNonShowLineTime: Double,
  val firstSpeechTime: Double,
  val opensWithMusic: Boolean,
  val evidence: String,
)

data class OutlineBreak(
  val lastShowLineTime: Double,
  val firstShowLineTime: Double,
  val evidence: String,
)

data class ShowEnd(
  val lastShowLineTime: Double,
  val evidence: String,
)

data class OutlineChapter(
  val time: Double,
  val title: String,
  val label: String? = null,
  val summary: String? = null,
)

data class Pause(
  val lastShowLineTime: Double,
  val firstShowLineTime: Double,
)

data class Consensus(
  val hosts: List<String>,
  val opensWithMusic: Boolean,
  val lastNonShowLineTime: Double,
  val firstSpeechTime: Double,
  val breaks: List<Pause>,
  val lastShowLineTime: Double,
  val chapters: List<OutlineChapter>,
  val voters: Int,
)

fun parse(text: String): OutlineRead? = runCatching { JsonMapperFactory.instance.readValue(text, OutlineRead::class.java) }.getOrNull()

fun lines(transcript: Transcript): List<TranscriptLine> {
  val starts = transcript.segments.map { it.start }
  val owners = transcript.words.groupBy { word -> maxOf(starts.indexOfLast { it <= word.start }, 0) }
  return transcript.segments.mapIndexed { index, segment ->
    TranscriptLine(segment.start, segment.end, segment.text, owners[index].orEmpty())
  }
}

fun music(
  lines: List<TranscriptLine>,
  edit: EditList?,
): List<TranscriptLine> = music(lines, edit?.music.orEmpty() + edit?.songs.orEmpty())

fun music(
  lines: List<TranscriptLine>,
  spans: List<KeptSpan>,
): List<TranscriptLine> =
  lines
    .map { line -> if (sung(line, spans)) line.copy(text = "", words = emptyList(), music = true) else line }
    .fold(emptyList()) { rows, line ->
      val last = rows.lastOrNull()
      if (line.music && last?.music == true) rows.dropLast(1) + last.copy(end = line.end) else rows + line
    }

private fun sung(
  line: TranscriptLine,
  spans: List<KeptSpan>,
): Boolean {
  if (spans.any { line.start >= it.start && line.start < it.end }) return true
  val letters = line.text.filter(Char::isLetter)
  return letters.count { Character.UnicodeScript.of(it.code) != Character.UnicodeScript.LATIN } * 2 > letters.length
}

private fun fixed(
  value: Double,
  digits: Int,
): String = if (value.isNaN()) "nan" else BigDecimal(value).setScale(digits, RoundingMode.HALF_EVEN).toPlainString()

private fun air(slot: Slot?): String {
  if (slot == null) return ""
  val time = { seconds: Long -> Instant.ofEpochSecond(seconds).atZone(TALLINN).format(AIR) }
  return " for ${time(slot.start)}-${time(slot.end)}"
}

fun describe(
  status: RecordingStatus,
  show: Show,
  lines: List<TranscriptLine>,
  events: List<AudioEvent>,
): String {
  val started = Instant.ofEpochSecond(0, (status.startedAt * 1e9).toLong()).atZone(TALLINN)
  val at = { seconds: Double -> started.plusNanos((seconds * 1e9).toLong()) }
  val clock = { seconds: Double -> at(seconds).format(CLOCK) }
  val inside = { seconds: Double -> seconds in show.from..show.to }
  val header =
    listOf(
      "Recording: Kuku Raadio live internet stream, ${at(show.from).format(DAY)}, ${show.minutes} minutes, " +
        "stream clock ${clock(show.from)}-${clock(show.to)}.",
      "The published schedule lists the talk show \"${show.name}\"${air(show.slot)}. " +
        "The stream lags the broadcast by about ${LAG.seconds} s.",
      "",
      "## Audio events from the loudness analysis",
      "Stretches of digital silence (below -75 dB for at least 30 ms) or quiet (below -50 dB for at least 0.3 s).",
      "before/after = median loudness in dB over the 3 s before and after the event.",
      "",
    )
  val found =
    events.filter { inside(it.start) }.map {
      "${fixed(it.start, 2).padStart(7)}-${fixed(it.end, 2).padStart(7)} s (${clock(it.start)})  ${it.kind.padEnd(15)}  " +
        "before ${fixed(it.before, 1).padStart(6)} dB  after ${fixed(it.after, 1).padStart(6)} dB"
    }
  val legend =
    listOf(
      "",
      "## Machine transcript (Estonian, no speaker labels, contains recognition errors)",
      "Each line: start-end in seconds from the start of the file, the stream clock at the start, the text.",
      "",
    )
  val spoken =
    lines
      .filter { inside(it.start) }
      .map { "${fixed(it.start, 1).padStart(7)}-${fixed(it.end, 1).padStart(7)} s (${clock(it.start)})  ${it.text}" }
  return (header + found + legend + spoken).joinToString("\n") + "\n"
}

private fun nearest(
  lines: List<TranscriptLine>,
  time: Double,
  edge: (TranscriptLine) -> Double,
): TranscriptLine? = lines.minByOrNull { abs(edge(it) - time) }?.takeIf { abs(edge(it) - time) <= 1 }

private fun printed(
  lines: List<TranscriptLine>,
  time: Double,
): Double? = (nearest(lines, time) { it.start } ?: nearest(lines, time) { it.end })?.start

private fun spoken(
  lines: List<TranscriptLine>,
  time: Double,
): Double =
  printed(lines, time)
    ?: checkNotNull(lines.filter { it.start <= time }.maxOfOrNull { it.start }) { "No transcript line starts before $time" }

fun match(
  read: OutlineRead,
  lines: List<TranscriptLine>,
): OutlineRead? =
  runCatching {
    val snap = { time: Double -> checkNotNull(printed(lines, time)) { "No transcript line starts or ends within 1 s of $time" } }
    read.copy(
      showStart = with(read.showStart) { copy(lastNonShowLineTime = snap(lastNonShowLineTime), firstSpeechTime = snap(firstSpeechTime)) },
      breaks = read.breaks.map { it.copy(lastShowLineTime = snap(it.lastShowLineTime), firstShowLineTime = snap(it.firstShowLineTime)) },
      showEnd = read.showEnd.copy(lastShowLineTime = snap(read.showEnd.lastShowLineTime)),
      chapters = read.chapters.map { it.copy(time = spoken(lines, it.time)) },
    )
  }.getOrNull()

private fun <T> majority(values: List<T>): T =
  values
    .groupingBy { it }
    .eachCount()
    .maxBy { it.value }
    .key

private fun medianLow(values: List<Double>): Double = values.sorted()[(values.size - 1) / 2]

fun consensus(reads: List<OutlineRead>): Consensus {
  require(reads.isNotEmpty()) { "No outline read to vote on" }
  val sorted = reads.map { read -> read.copy(breaks = read.breaks.sortedBy { it.lastShowLineTime }) }
  val count = majority(sorted.map { it.breaks.size })
  val voters = sorted.filter { it.breaks.size == count }
  val median = { pick: (OutlineRead) -> Double -> medianLow(voters.map(pick)) }
  return Consensus(
    hosts = voters.first().hosts,
    opensWithMusic = majority(voters.map { it.showStart.opensWithMusic }),
    lastNonShowLineTime = median { it.showStart.lastNonShowLineTime },
    firstSpeechTime = median { it.showStart.firstSpeechTime },
    breaks = List(count) { index -> Pause(median { it.breaks[index].lastShowLineTime }, median { it.breaks[index].firstShowLineTime }) },
    lastShowLineTime = median { it.showEnd.lastShowLineTime },
    chapters = voters.first().chapters,
    voters = voters.size,
  )
}

fun agree(
  reads: List<OutlineRead?>,
  lines: List<TranscriptLine>,
): Boolean {
  val placed = reads.map { read -> read?.let { match(it, lines) }?.let(::timing) }
  return null !in placed && placed.distinct().size == 1
}

private fun timing(read: OutlineRead): Consensus = consensus(listOf(read)).copy(hosts = emptyList(), chapters = emptyList())
