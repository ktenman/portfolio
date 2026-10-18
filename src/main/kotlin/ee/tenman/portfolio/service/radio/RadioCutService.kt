package ee.tenman.portfolio.service.radio

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

const val TRANSCRIPT = "transcript only"
private const val DIP = "level dip"

fun firstWord(line: TranscriptLine): Double {
  val words = line.words
  if (words.size > 1 && words[1].start - words[0].end > 3) return words[1].start - 0.4
  return words.firstOrNull()?.start ?: line.start
}

fun lastWord(line: TranscriptLine): Double = line.words.lastOrNull()?.end ?: line.end

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

@Service
class RadioCutService {
  fun boundaries(
    timeline: Timeline,
    db: DoubleArray,
    outline: Consensus,
    lines: List<TranscriptLine>,
  ): List<Boundary> {
    val line = lines.associateBy { it.start }
    val at = { time: Double -> checkNotNull(line[time]) { "No transcript line starts at $time s" } }
    val returns = outline.breaks.map { firstWord(at(it.firstShowLineTime)) }
    val pauses = pauses(db, outline, at, returns, jingle(timeline, db, returns))
    val end = "show ends" to leave(db, lastWord(at(outline.lastShowLineTime)), 15.0)
    val placed = listOf("show starts" to start(db, outline, at)) + pauses + end
    return ordered(placed.map { (label, cut) -> Boundary(label, rounded(cut.first), cut.second, cut.second == TRANSCRIPT) })
  }

  private fun pauses(
    db: DoubleArray,
    outline: Consensus,
    at: (Double) -> TranscriptLine,
    returns: List<Double>,
    shared: SharedJingle?,
  ): List<Pair<String, Pair<Double, String>>> =
    outline.breaks.flatMapIndexed { index, pause ->
      listOf(
        "break ${index + 1} starts" to leave(db, lastWord(at(pause.lastShowLineTime)), 6.0),
        "break ${index + 1} ends" to back(db, returns[index], shared, index),
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
    db: DoubleArray,
    word: Double,
    shared: SharedJingle?,
    index: Int,
  ): Pair<Double, String> {
    val cut = shared?.cuts?.get(index)
    if (cut != null) return cut to "station jingle, r=${BigDecimal(shared.r).setScale(2, RoundingMode.HALF_EVEN)}"
    val dip = dips(db, word - 8, word - 0.3).lastOrNull() ?: return word - 0.3 to TRANSCRIPT
    return dip to DIP
  }
}
