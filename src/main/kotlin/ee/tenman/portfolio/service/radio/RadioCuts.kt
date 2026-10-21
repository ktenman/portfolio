package ee.tenman.portfolio.service.radio

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude

const val PROGRAMME = "Keskpäevatund"
private const val TOPIC_LEAD = 30.0
private const val SHORTEST = 0.01
private val BREAK_STARTS = Regex("break \\d+ starts")

enum class BoundaryMethod(
  val prefix: String,
) {
  DIGITAL_SILENCE(DIGITAL),
  STATION_JINGLE("station jingle"),
  LEVEL_DIP("level dip"),
  TRANSCRIPT_ONLY(TRANSCRIPT),
  MANUAL("manual"),
  ;

  companion object {
    fun of(method: String): BoundaryMethod = entries.first { method.startsWith(it.prefix) }
  }
}

enum class CutKind(
  val title: String,
) {
  BEFORE("Enne saadet"),
  BREAK("Reklaamipaus"),
  BETWEEN("Saadete vahel"),
  AFTER("Pärast saadet"),
  MANUAL("Käsitsi lõige"),
}

data class Cut(
  val kind: CutKind,
  val title: String,
  val start: Double,
  val end: Double,
  val methods: List<BoundaryMethod>,
  val review: Boolean,
  val removed: Boolean,
  val jingle: CutJingle? = null,
  @get:JsonIgnore val match: JingleMatch? = null,
)

data class CutJingle(
  val r: Double,
  val saved: Boolean,
)

data class TimelineEntry(
  val start: Double,
  val end: Double,
  val title: String?,
  val cut: Boolean,
  val label: String? = null,
  val summary: String? = null,
)

data class TranscriptSection(
  val start: Double,
  val end: Double,
  val title: String,
  @field:JsonInclude(JsonInclude.Include.ALWAYS)
  val cut: Int?,
  @field:JsonInclude(JsonInclude.Include.ALWAYS)
  val episodeStart: Double?,
  val lines: List<TranscriptLine>,
)

data class ManualCut(
  val start: Double,
  val end: Double,
)

data class CutReview(
  val removed: List<Int>,
)

data class JingleReview(
  val saved: Boolean,
)

data class CutContext(
  val before: TranscriptLine?,
  val after: TranscriptLine?,
)

fun mark(
  lines: List<TranscriptLine>,
  cuts: List<Cut>,
): List<TranscriptLine> {
  val removed = cuts.filter { it.removed }
  return lines.map { line ->
    line.copy(
      split =
      removed.firstOrNull { cut ->
        listOf(cut.start, cut.end).any {
          firstWord(line) < it &&
    it < lastWord(line)
            }
          },
      )
      }
}

fun context(
  cut: Cut,
  lines: List<TranscriptLine>,
): CutContext = CutContext(lines.lastOrNull { it.start < cut.start }, lines.firstOrNull { it.end > cut.end })

fun cuts(
  edit: EditList?,
  total: Double,
): List<Cut> {
  if (edit == null) return emptyList()
  val shows =
    edit.boundaries
    .groupBy { it.programme }
    .values
    .map { show -> show.associateBy { it.boundary } }
  val starts = shows.mapNotNull { it["show starts"] }
  val ends = shows.mapNotNull { it["show ends"] }
  val before = starts.firstOrNull()?.let { cut(CutKind.BEFORE, 0.0, it.seconds, listOf(it), edit.keep) }
  val pauses =
    shows.flatMap { at ->
    (1..at.keys.count { BREAK_STARTS.matches(it) }).mapNotNull { pause(at["break $it starts"], at["break $it ends"], edit.keep) }
  }
  val between =
    ends.zip(starts.drop(1)).map { (end, start) ->
    cut(CutKind.BETWEEN, end.seconds, start.seconds, listOf(end, start), edit.keep)
  }
  val after = ends.lastOrNull()?.let { cut(CutKind.AFTER, it.seconds, total, listOf(it), edit.keep) }
  val manual = edit.manual.map { manual(it, edit.keep) }
  return (listOfNotNull(before) + pauses + between + listOfNotNull(after)).sortedBy { it.start }.filter { it.end - it.start > SHORTEST } +
    manual
}

private fun manual(
  span: ManualCut,
  keep: List<KeptSpan>,
): Cut {
  val methods = listOf(BoundaryMethod.MANUAL, BoundaryMethod.MANUAL)
  return Cut(CutKind.MANUAL, CutKind.MANUAL.title, span.start, span.end, methods, false, removed(span.start, span.end, keep))
}

private fun removed(
  start: Double,
  end: Double,
  keep: List<KeptSpan>,
): Boolean {
  val middle = (start + end) / 2
  return keep.none { middle >= it.start && middle < it.end }
}

private fun pause(
  from: Boundary?,
  to: Boundary?,
  keep: List<KeptSpan>,
): Cut? {
  if (from == null || to == null) return null
  return cut(CutKind.BREAK, from.seconds, to.seconds, listOf(from, to), keep)
}

private fun cut(
  kind: CutKind,
  start: Double,
  end: Double,
  ends: List<Boundary>,
  keep: List<KeptSpan>,
): Cut =
  Cut(
    kind = kind,
    title = kind.title,
    start = start,
    end = end,
    methods = ends.map { BoundaryMethod.of(it.method) },
    review = ends.any { it.review },
    removed = removed(start, end, keep),
    match = ends.last().jingle,
  )

fun keep(
  cuts: List<Cut>,
  removed: List<Int>,
  total: Double,
): List<KeptSpan> {
  require(removed.all { it in cuts.indices }) { "Cuts $removed are not all among the ${cuts.size} cuts of the recording" }
  return between(removed.map { cuts[it] }, total)
}

private fun between(
  gone: List<Cut>,
  total: Double,
): List<KeptSpan> {
  val edges = gone.sortedBy { it.start }.flatMap { listOf(it.start, it.end) }
  return (listOf(0.0) + edges + total)
    .chunked(2)
    .map { (start, end) -> KeptSpan(start, end) }
    .filter { it.end > it.start }
}

fun timeline(
  found: List<Cut>,
  chapters: List<EpisodeChapter>,
  total: Double,
): List<TimelineEntry> {
  val topics = chapters.sortedBy { it.start }
  val marked = found.map { TimelineEntry(it.start, it.end, it.title, true) }
  return (marked + between(found, total).flatMap { split(it, topics) }).sortedBy { it.start }
}

private fun split(
  kept: KeptSpan,
  topics: List<EpisodeChapter>,
): List<TimelineEntry> {
  val opening = topics.lastOrNull { it.start <= kept.start + TOPIC_LEAD }
  val inside = topics.filter { it.start > kept.start + TOPIC_LEAD && it.start < kept.end }
  val starts = listOf(kept.start) + inside.map { it.start }
  val ends = inside.map { it.start } + kept.end
  return (listOf(opening) + inside).zip(starts.zip(ends)).map { (mark, span) ->
    TimelineEntry(span.first, span.second, mark?.title, false, mark?.label, mark?.summary)
  }
}

fun sections(
  timeline: List<TimelineEntry>,
  cuts: List<Cut>,
  audible: List<KeptSpan>,
  lines: List<TranscriptLine>,
  programme: String,
): List<TranscriptSection> =
  timeline.mapIndexed { index, entry ->
    val last = index == timeline.lastIndex
    TranscriptSection(
      start = entry.start,
      end = entry.end,
      title = entry.title ?: programme,
      cut = if (entry.cut) cuts.indexOfFirst { it.start == entry.start && it.end == entry.end }.takeIf { it >= 0 } else null,
      episodeStart = episodeStart(entry, audible),
      lines = lines.filter { it.start >= entry.start && (last || it.start < entry.end) },
    )
  }

fun heard(
  timeline: List<TimelineEntry>,
  audible: List<KeptSpan>,
): List<TimelineEntry> =
  timeline
    .mapNotNull { entry -> episodeStart(entry, audible)?.let { entry.copy(start = it, end = it + overlap(entry, audible)) } }
    .fold(emptyList(), ::join)

private fun overlap(
  entry: TimelineEntry,
  audible: List<KeptSpan>,
): Double = audible.sumOf { maxOf(0.0, minOf(it.end, entry.end) - maxOf(it.start, entry.start)) }

private fun join(
  joined: List<TimelineEntry>,
  entry: TimelineEntry,
): List<TimelineEntry> {
  val last = joined.lastOrNull()
  if (last == null || last.title != entry.title || last.cut != entry.cut) return joined + entry
  return joined.dropLast(1) + last.copy(end = entry.end)
}

private fun episodeStart(
  entry: TimelineEntry,
  audible: List<KeptSpan>,
): Double? {
  val span = audible.firstOrNull { it.end > entry.start && it.start < entry.end } ?: return null
  return audible.takeWhile { it !== span }.sumOf { it.end - it.start } + maxOf(0.0, entry.start - span.start)
}
