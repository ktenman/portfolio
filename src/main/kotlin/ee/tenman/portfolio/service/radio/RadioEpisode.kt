package ee.tenman.portfolio.service.radio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

const val FADE = 0.015
private const val ESCAPED = "\\=;#"

data class Piece(
  val index: Int,
  val start: Double,
  val end: Double,
  val offset: Double,
)

fun position(seconds: Double): String = "${(seconds / 60).toInt()}:${(seconds % 60).toInt().toString().padStart(2, '0')}"

private fun joins(keep: List<Pair<Double, Double>>): List<Double> = keep.runningFold(0.0) { total, (a, b) -> total + b - a }.dropLast(1)

fun chapters(
  keep: List<Pair<Double, Double>>,
  topics: List<OutlineChapter>,
): List<Pair<Double, String>> {
  val seams = joins(keep)
  val total = keep.sumOf { it.second - it.first }
  val clean = { t: Double ->
    keep.zip(seams).firstOrNull { (span, _) -> t < span.second }?.let { (span, seam) -> seam + max(t - span.first, 0.0) } ?: total
  }
  val order = compareBy<Pair<Double, String>>({ it.first }, { it.second })
  val titled = topics.ifEmpty { listOf(OutlineChapter(0.0, PROGRAMME)) }.map { clean(it.time) to it.title }.sortedWith(order)
  val opened = listOf(0.0 to titled.first().second) + titled.drop(1)
  val marks =
    seams.withIndex().drop(1).map { (index, seam) ->
      seam - 5 to "Lõige $index (${position(seam)}) – ${opened.last { it.first <= seam + 30 }.second}"
    }
  val untouched = opened.filterNot { (t, _) -> seams.drop(1).any { seam -> t in seam - 5..seam + 30 } }
  return (marks + untouched).sortedWith(order)
}

private fun escape(text: String): String = text.replace("\n", " ").map { if (it in ESCAPED) "\\$it" else "$it" }.joinToString("")

fun metadata(
  marks: List<Pair<Double, String>>,
  total: Double,
): String {
  val ends = marks.drop(1).map { it.first } + total
  return ";FFMETADATA1\n" +
    marks.zip(ends).joinToString("") { (mark, end) ->
      val start = round(mark.first * 1000).toLong()
      "[CHAPTER]\nTIMEBASE=1/1000\nSTART=$start\nEND=${round(end * 1000).toLong()}\ntitle=${escape(mark.second)}\n"
    }
}

fun pieces(
  spans: List<Span>,
  keep: List<Pair<Double, Double>>,
): List<Piece> =
  keep.flatMap { (a, b) ->
    spans.mapIndexedNotNull { index, span ->
      Piece(index, max(a, span.start), min(b, span.end), span.offset).takeIf { it.end - it.start >= 2 * FADE }
    }
  }

fun audible(
  spans: List<Span>,
  keep: List<Pair<Double, Double>>,
): List<Pair<Double, Double>> =
  pieces(spans, keep).fold(emptyList()) { merged, piece ->
    val last = merged.lastOrNull()
    val touching = last != null && abs(last.second - piece.start) < 1e-6
    if (touching) merged.dropLast(1) + (last.first to piece.end) else merged + (piece.start to piece.end)
  }

data class KeptSpan(
  val start: Double,
  val end: Double,
)

data class EpisodeChapter(
  val start: Double,
  val title: String,
)

data class OutlineSummary(
  val model: String?,
  val reads: Int,
  val matched: Int,
  val voters: Int,
)

data class EditList(
  val keep: List<KeptSpan>,
  val durationSeconds: Double,
  val parts: List<RecordingPart>,
  val boundaries: List<Boundary>,
  val hosts: List<String>,
  val outline: OutlineSummary,
  val chapters: List<EpisodeChapter>,
  val reviewed: Boolean,
  val renderedAt: String,
  val topics: List<EpisodeChapter> = emptyList(),
  val music: List<KeptSpan> = emptyList(),
)

data class EpisodeFailure(
  val reason: String,
  val failedAt: String,
)
