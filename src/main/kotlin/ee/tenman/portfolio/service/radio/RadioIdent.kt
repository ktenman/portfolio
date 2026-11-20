package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.Station
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

const val IDENT_LEAD = 0.35
const val IDENT_TAIL = 0.05
const val IDENTS_FILE = "idents.json"
private const val STEP = 800
private const val QUIET_DROP = 20.0
private const val REACH = 2.0
private const val SHORTEST = 3.0
private const val LONGEST = 7.0
private const val WORD_MARGIN = 0.2

data class Ident(
  val station: Station,
  val programme: String,
  val file: String,
  val seconds: Double,
  val savedAt: String,
)

data class IdentCut(
  val programme: String,
  val start: Double,
  val seconds: Double,
  val r: Double,
)

fun identCuts(
  timeline: Timeline,
  boundaries: List<Boundary>,
  words: List<TranscriptWord>,
): List<IdentCut> =
  boundaries
    .mapNotNull { identCut(timeline, it, words) }
    .groupBy { it.programme }
    .map { (_, found) -> found.maxWith(compareBy({ it.seconds }, { it.r })) }

fun idents(directory: Path): List<Ident> =
  read<List<Ident>>(directory.resolve(IDENTS_FILE)).orEmpty().filter { Files.exists(directory.resolve(it.file)) }

fun led(
  clips: List<DigestClip>,
  idents: List<Ident>,
): List<DigestClip> = clips.map { it.copy(ident = ident(it.story.best, idents)) }

fun gaps(clips: List<DigestClip>): List<Double> =
  clips.drop(1).map { clip -> clip.ident?.let { rounded(IDENT_LEAD + it.seconds + IDENT_TAIL) } ?: PAUSE_SECONDS } + PAUSE_SECONDS

fun join(
  start: Double,
  end: Double,
  pause: Path,
  ident: Path?,
): List<Span> {
  if (ident == null) return listOf(Span(start, end, pause, start, pause))
  val from = start + IDENT_LEAD
  val to = end - IDENT_TAIL
  return listOf(Span(start, from, pause, start, pause), Span(from, to, ident, from, ident), Span(to, end, pause, to, pause))
}

private fun ident(
  row: DigestRow,
  idents: List<Ident>,
): Ident? {
  val aired = idents.filter { it.station == row.station }
  return aired.firstOrNull { it.programme == row.programme } ?: aired.maxByOrNull { it.savedAt }
}

private fun identCut(
  timeline: Timeline,
  boundary: Boundary,
  words: List<TranscriptWord>,
): IdentCut? {
  val match = boundary.jingle ?: return null
  val span = timeline.spans.firstOrNull { match.start >= it.start && match.start < it.end } ?: return null
  val at = sample(match.start) / STEP
  val quiet = levels(timeline, at, at + sample(REACH) / STEP).sorted().let { it[it.size / 2] } - QUIET_DROP
  val start = onset(timeline, max(sample(span.start) / STEP, at - sample(REACH) / STEP), at, quiet) ?: match.start
  val word = words.firstOrNull { it.start >= max(boundary.seconds, match.start) }?.start
  val limit = minOf(word?.minus(WORD_MARGIN) ?: span.end, start + LONGEST, span.end)
  val end = hush(timeline, start + SHORTEST, limit, quiet) ?: limit
  if (end - start < SHORTEST) return null
  return IdentCut(boundary.programme ?: PROGRAMME, rounded(start), rounded(end - start), match.r)
}

private fun onset(
  timeline: Timeline,
  from: Long,
  to: Long,
  quiet: Double,
): Double? = levels(timeline, from, to).indexOfLast { it < quiet }.takeIf { it >= 0 }?.let { seconds(from + it + 1) }

private fun hush(
  timeline: Timeline,
  from: Double,
  to: Double,
  quiet: Double,
): Double? {
  val first = Math.ceilDiv(sample(from), STEP.toLong())
  return levels(timeline, first, sample(to) / STEP).indexOfFirst { it < quiet }.takeIf { it >= 0 }?.let { seconds(first + it) }
}

private fun seconds(frame: Long): Double = frame * STEP / RATE.toDouble()

private fun levels(
  timeline: Timeline,
  from: Long,
  to: Long,
): List<Double> {
  val count = max(to - from, 0).toInt()
  val block = timeline.read(from * STEP, count * STEP)
  return List(count) { frame ->
    val power = (0 until STEP).sumOf { block[frame * STEP + it] * block[frame * STEP + it] } / STEP
    20 * log10(max(sqrt(power), 1.0) / 32768)
  }
}
