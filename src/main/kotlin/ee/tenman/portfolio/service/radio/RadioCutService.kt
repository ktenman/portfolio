package ee.tenman.portfolio.service.radio

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

const val TRANSCRIPT = "transcript only"
private const val DIP = "level dip"
private const val REACH = 2.0
private const val PAUSE = 1.5

fun firstWord(line: TranscriptLine): Double {
  val words = line.words
  if (words.size > 1 && words[1].start - words[0].end > 3) return words[1].start - 0.4
  return words.firstOrNull()?.start ?: line.start
}

fun lastWord(line: TranscriptLine): Double = line.words.lastOrNull()?.end ?: line.end

fun cue(
  db: DoubleArray,
  lines: List<TranscriptLine>,
  time: Double,
): Double {
  val previous = lines.lastOrNull { it.start < time } ?: return time
  return quietest(db, max(lastWord(previous), time - PAUSE), time) ?: time
}

fun leave(
  db: DoubleArray,
  wordEnd: Double,
): Pair<Double, String> {
  val gap = silences(db, wordEnd - 0.5, wordEnd + PAUSE).firstOrNull { it.first >= wordEnd - 0.5 }
  if (gap != null) return min((gap.first + gap.second) / 2, gap.first + 0.3) to DIGITAL
  val dip = dips(db, wordEnd, wordEnd + PAUSE).firstOrNull() ?: return wordEnd + 0.2 to TRANSCRIPT
  return dip to DIP
}

fun snap(
  db: DoubleArray,
  seconds: Double,
): Double {
  if (seconds < 0 || seconds > db.size * FRAME) return seconds
  val nearest = { found: List<Double> -> found.minByOrNull { abs(it - seconds) } }
  val quiet = silences(db, seconds - REACH, seconds + REACH).map { (it.first + it.second) / 2 }
  return nearest(quiet) ?: nearest(dips(db, seconds - REACH, seconds + REACH)) ?: seconds
}

@Service
class RadioCutService {
  fun boundaries(
    timeline: Timeline,
    db: DoubleArray,
    outline: Consensus,
    lines: List<TranscriptLine>,
    library: List<SavedJingle>,
    repeated: (Double, Double) -> Double?,
  ): List<Boundary> {
    val line = lines.associateBy { it.start }
    val at = { time: Double -> checkNotNull(line[time]) { "No transcript line starts at $time s" } }
    val returns = outline.breaks.map { firstWord(at(it.firstShowLineTime)) }
    val shared by lazy { jingle(timeline, returns) }
    val ends = returns.mapIndexed { index, back -> recognise(timeline, back, library) ?: shared?.get(index) }
    val start = placed("show starts", firstWord(at(outline.firstSpeechTime)) - 0.3 to TRANSCRIPT)
    val end = ended("show ends", db, at(outline.lastShowLineTime), repeated)
    return ordered(listOf(start) + pauses(db, outline, at, lines, ends, repeated) + end)
  }

  private fun placed(
    label: String,
    cut: Pair<Double, String>,
  ): Boundary = Boundary(label, rounded(cut.first), cut.second, cut.second == TRANSCRIPT)

  private fun ended(
    label: String,
    db: DoubleArray,
    line: TranscriptLine,
    repeated: (Double, Double) -> Double?,
  ): Boundary {
    val cut = leave(db, lastWord(line))
    val start = repeated(line.words.lastOrNull()?.start ?: line.end, cut.first) ?: return placed(label, cut)
    return Boundary(label, rounded(start), BoundaryMethod.REPEATED_AUDIO.prefix, cut.second == TRANSCRIPT)
  }

  private fun pauses(
    db: DoubleArray,
    outline: Consensus,
    at: (Double) -> TranscriptLine,
    lines: List<TranscriptLine>,
    ends: List<JingleMatch?>,
    repeated: (Double, Double) -> Double?,
  ): List<Boundary> =
    outline.breaks.flatMapIndexed { index, pause ->
      listOf(
        ended("break ${index + 1} starts", db, at(pause.lastShowLineTime), repeated),
        back("break ${index + 1} ends", db, lines, firstWord(at(pause.firstShowLineTime)), ends[index]),
      )
    }

  private fun ordered(found: List<Boundary>): List<Boundary> {
    check(found.zipWithNext().all { (a, b) -> a.seconds < b.seconds }) {
      "Boundaries are out of order: ${found.map { it.boundary to it.seconds }}"
    }
    return found
  }

  private fun back(
    label: String,
    db: DoubleArray,
    lines: List<TranscriptLine>,
    word: Double,
    match: JingleMatch?,
  ): Boundary {
    val after = lines.lastOrNull { lastWord(it) <= word }?.let(::lastWord) ?: 0.0
    val dip = dips(db, max(after, word - PAUSE), word - 0.3).lastOrNull()
    val cut = dip?.let { it to DIP } ?: (word - 0.3 to TRANSCRIPT)
    if (match == null) return placed(label, cut)
    return jingled(label, cut.first, match)
  }

  private fun jingled(
    label: String,
    seconds: Double,
    match: JingleMatch,
  ): Boundary {
    val kept = match.copy(start = round(match.start * 1000) / 1000, r = rounded(match.r))
    val method = "${BoundaryMethod.STATION_JINGLE.prefix}, r=${BigDecimal(kept.r).setScale(2, RoundingMode.HALF_EVEN)}"
    return Boundary(label, rounded(seconds), method, false, kept)
  }
}
