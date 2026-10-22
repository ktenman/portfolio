package ee.tenman.portfolio.service.radio

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.max
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
  limit: Double,
): Pair<Double, String> {
  val gap = silences(db, wordEnd - 0.5, wordEnd + limit).firstOrNull { it.first >= wordEnd - 0.5 }
  if (gap != null) return (gap.first + gap.second) / 2 to DIGITAL
  val dip = dips(db, wordEnd, wordEnd + limit).firstOrNull() ?: return wordEnd + 0.2 to TRANSCRIPT
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
  ): List<Boundary> {
    val line = lines.associateBy { it.start }
    val at = { time: Double -> checkNotNull(line[time]) { "No transcript line starts at $time s" } }
    val returns = outline.breaks.map { firstWord(at(it.firstShowLineTime)) }
    val shared by lazy { jingle(timeline, returns) }
    val ends = returns.mapIndexed { index, back -> recognise(timeline, back, library) ?: shared?.get(index) }
    val start = placed("show starts", start(db, outline, at))
    val end = placed("show ends", leave(db, lastWord(at(outline.lastShowLineTime)), 15.0))
    return ordered(listOf(start) + pauses(db, outline, at, returns, ends) + end)
  }

  private fun placed(
    label: String,
    cut: Pair<Double, String>,
  ): Boundary = Boundary(label, rounded(cut.first), cut.second, cut.second == TRANSCRIPT)

  private fun pauses(
    db: DoubleArray,
    outline: Consensus,
    at: (Double) -> TranscriptLine,
    returns: List<Double>,
    ends: List<JingleMatch?>,
  ): List<Boundary> =
    outline.breaks.flatMapIndexed { index, pause ->
      listOf(
        placed("break ${index + 1} starts", leave(db, lastWord(at(pause.lastShowLineTime)), 6.0)),
        back("break ${index + 1} ends", db, returns[index], ends[index]),
      )
    }

  private fun ordered(found: List<Boundary>): List<Boundary> {
    check(found.zipWithNext().all { (a, b) -> a.seconds < b.seconds }) {
      "Boundaries are out of order: ${found.map { it.boundary to it.seconds }}"
    }
    return found
  }

  private fun start(
    db: DoubleArray,
    outline: Consensus,
    at: (Double) -> TranscriptLine,
  ): Pair<Double, String> {
    val first = firstWord(at(outline.firstSpeechTime))
    if (!outline.opensWithMusic) return first - 0.3 to TRANSCRIPT
    val last = lastWord(at(outline.lastNonShowLineTime))
    return leave(db, last, first - last)
  }

  private fun back(
    label: String,
    db: DoubleArray,
    word: Double,
    match: JingleMatch?,
  ): Boundary {
    if (match != null) return jingled(label, db, match)
    val dip = dips(db, word - 8, word - 0.3).lastOrNull() ?: return placed(label, word - 0.3 to TRANSCRIPT)
    return placed(label, dip to DIP)
  }

  private fun jingled(
    label: String,
    db: DoubleArray,
    match: JingleMatch,
  ): Boundary {
    val kept = match.copy(start = round(match.start * 1000) / 1000, r = rounded(match.r))
    val method = "${BoundaryMethod.STATION_JINGLE.prefix}, r=${BigDecimal(kept.r).setScale(2, RoundingMode.HALF_EVEN)}"
    return Boundary(label, rounded(valley(db, match.start - 0.6, match.start + 0.05)), method, false, kept)
  }
}
