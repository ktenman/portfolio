package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.Station
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

const val IDENTS_FILE = "idents.json"
private const val IDENT_LEAD = 0.35
private const val IDENT_TAIL = 0.05
private const val STEP = 800
private const val QUIET_DROP = 20.0
private const val REACH = 2.0
private const val SHORTEST = 3.0
private const val LONGEST = 7.0
private const val WORD_MARGIN = 0.2
private const val STING = 2.5
private const val NAMED = 4.5
private const val NAME_WORDS = 6
private const val NEAR = 0.1
private const val REST = 1.0
private const val MUSICAL = 0.5
private const val SLACK = 0.25
private const val OPENING = "show starts"
private val RESUMED = Regex("break \\d+ ends")
private val log = LoggerFactory.getLogger(Ident::class.java)

data class Ident(
  val station: Station,
  val programme: String,
  val file: String,
  val seconds: Double,
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
  frames: List<SoundFrame>,
): List<IdentCut> {
  val jingles = best(boundaries.mapNotNull { identCut(timeline, it, words) })
  val stings = best(boundaries.mapNotNull { sting(timeline, it, words, frames) })
  val openings = best(boundaries.mapNotNull { opening(timeline, it, words, frames) })
  return (openings + stings + jingles).values.toList()
}

fun idents(directory: Path): List<Ident> =
  runCatching { read<List<Ident>>(directory.resolve(IDENTS_FILE)) }
    .onFailure { log.warn("Cannot read the saved jingles in $directory", it) }
    .getOrNull()
    .orEmpty()
    .filter { Files.exists(directory.resolve(it.file)) }

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
): Ident? = idents.firstOrNull { it.station == row.station && it.programme == row.programme }

private fun identCut(
  timeline: Timeline,
  boundary: Boundary,
  words: List<TranscriptWord>,
): IdentCut? {
  val match = boundary.jingle ?: return null
  val span = span(timeline, match.start) ?: return null
  val at = frame(match.start)
  val quiet = quiet(timeline, at)
  val start = onset(timeline, max(frame(span.start), at - frame(REACH)), at, quiet) ?: match.start
  val word = words.firstOrNull { it.start >= max(boundary.seconds, match.start) }?.start
  val end = end(timeline, span, start, word, quiet)
  if (end - start < SHORTEST) return null
  val programme = boundary.programme ?: PROGRAMME
  val voice = words.filter { it.start in start..<end }
  if (voice.isNotEmpty() && closing(voice, letters(programme)) == null) return null
  return IdentCut(programme, rounded(start), rounded(end - start), match.r)
}

private fun sting(
  timeline: Timeline,
  boundary: Boundary,
  words: List<TranscriptWord>,
  frames: List<SoundFrame>,
): IdentCut? {
  if (boundary.jingle != null || !RESUMED.matches(boundary.boundary)) return null
  val programme = boundary.programme ?: PROGRAMME
  val start = boundary.seconds
  val span = span(timeline, start) ?: return null
  val later = words.filter { it.start >= start - NEAR }
  val count = named(later, letters(programme), start + NAMED) ?: return null
  val end = end(timeline, span, start, later.getOrNull(count)?.start, quiet(timeline, frame(start)))
  return scored(programme, start, end - start, frames)
}

private fun opening(
  timeline: Timeline,
  boundary: Boundary,
  words: List<TranscriptWord>,
  frames: List<SoundFrame>,
): IdentCut? {
  if (boundary.boundary != OPENING) return null
  val programme = boundary.programme ?: PROGRAMME
  val earlier = words.filter { it.end <= boundary.seconds + NEAR }
  val count = closing(earlier, letters(programme)) ?: return null
  val host = words.firstOrNull { it.start >= boundary.seconds - NEAR }?.start ?: boundary.seconds
  val previous = earlier.getOrNull(earlier.size - count - 1)?.end ?: 0.0
  val start = max(previous + WORD_MARGIN, host - WORD_MARGIN - LONGEST - REST)
  val span = span(timeline, start)?.takeIf { earlier[earlier.size - count].start >= start } ?: return null
  return scored(programme, start, minOf(host - WORD_MARGIN, start + LONGEST, span.end) - start, frames)
}

private fun scored(
  programme: String,
  start: Double,
  seconds: Double,
  frames: List<SoundFrame>,
): IdentCut? {
  val music = frames.filter { it.at >= start && it.at <= start + seconds + SLACK }.map { it.music }.average()
  if (seconds < STING || music.isNaN() || music < MUSICAL) return null
  return IdentCut(programme, rounded(start), rounded(seconds), rounded(music))
}

private fun named(
  words: List<TranscriptWord>,
  name: String,
  limit: Double,
): Int? =
  words
    .take(NAME_WORDS)
    .takeWhile { it.start <= limit }
    .runningFold("") { said, word -> said + letters(word.word) }
    .indexOfFirst { it.endsWith(name) }
    .takeIf { it > 0 }

private fun closing(
  words: List<TranscriptWord>,
  name: String,
): Int? = (1..min(NAME_WORDS, words.size)).firstOrNull { count -> words.takeLast(count).joinToString("") { letters(it.word) } == name }

private fun letters(text: String): String = fold(text).filter { it.isLetterOrDigit() }

private fun best(found: List<IdentCut>): Map<String, IdentCut> =
  found.groupBy { it.programme }.mapValues { (_, same) -> same.maxWith(compareBy({ it.seconds }, { it.r })) }

fun span(
  timeline: Timeline,
  at: Double,
): Span? = timeline.spans.firstOrNull { at >= it.start && at < it.end }

private fun quiet(
  timeline: Timeline,
  at: Long,
): Double = levels(timeline, at, at + frame(REACH)).sorted().let { it[it.size / 2] } - QUIET_DROP

private fun end(
  timeline: Timeline,
  span: Span,
  start: Double,
  word: Double?,
  quiet: Double,
): Double {
  val limit = minOf(word?.minus(WORD_MARGIN) ?: span.end, start + LONGEST, span.end)
  return hush(timeline, start + SHORTEST, limit, quiet) ?: limit
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
  return levels(timeline, first, frame(to)).indexOfFirst { it < quiet }.takeIf { it >= 0 }?.let { seconds(first + it) }
}

private fun seconds(frame: Long): Double = frame * STEP / RATE.toDouble()

private fun frame(seconds: Double): Long = sample(seconds) / STEP

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
