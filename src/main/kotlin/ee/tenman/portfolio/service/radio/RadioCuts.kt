package ee.tenman.portfolio.service.radio

const val PROGRAMME = "Keskpäevatund"
private const val TOPIC_LEAD = 30.0
private const val SHORTEST = 0.01
private val BREAK_STARTS = Regex("break \\d+ starts")

enum class CutKind(
  val title: String,
) {
  BEFORE("Enne saadet"),
  BREAK("Reklaamipaus"),
  AFTER("Pärast saadet"),
}

data class Cut(
  val kind: CutKind,
  val title: String,
  val start: Double,
  val end: Double,
  val methods: List<String>,
  val review: Boolean,
  val removed: Boolean,
)

data class TimelineEntry(
  val start: Double,
  val end: Double,
  val title: String,
  val cut: Boolean,
)

data class CutReview(
  val removed: List<Int>,
)

data class CutContext(
  val cut: Cut,
  val before: TranscriptLine?,
  val after: TranscriptLine?,
)

fun mark(
  lines: List<TranscriptLine>,
  cuts: List<Cut>,
): List<TranscriptLine> {
  val edges = cuts.filter { it.removed }.flatMap { cut -> listOf(cut.start to cut, cut.end to cut) }
  return lines.map { line -> line.copy(split = edges.firstOrNull { (at) -> firstWord(line) < at && at < lastWord(line) }?.second) }
}

fun context(
  cut: Cut,
  lines: List<TranscriptLine>,
): CutContext = CutContext(cut, lines.lastOrNull { it.start < cut.start }, lines.firstOrNull { it.end > cut.end })

fun cuts(
  edit: EditList?,
  total: Double,
): List<Cut> {
  if (edit == null) return emptyList()
  val at = edit.boundaries.associateBy { it.boundary }
  val count = edit.boundaries.count { BREAK_STARTS.matches(it.boundary) }
  val before = at["show starts"]?.let { cut(CutKind.BEFORE, 0.0, it.seconds, listOf(it), edit.keep) }
  val pauses = (1..count).mapNotNull { pause(at["break $it starts"], at["break $it ends"], edit.keep) }
  val after = at["show ends"]?.let { cut(CutKind.AFTER, it.seconds, total, listOf(it), edit.keep) }
  return (listOfNotNull(before) + pauses + listOfNotNull(after)).filter { it.end - it.start > SHORTEST }
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
): Cut {
  val middle = (start + end) / 2
  return Cut(
    kind = kind,
    title = kind.title,
    start = start,
    end = end,
    methods = ends.map { it.method },
    review = ends.any { it.review },
    removed = keep.none { middle >= it.start && middle < it.end },
  )
}

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
  edit: EditList?,
  total: Double,
): List<TimelineEntry> {
  val found = cuts(edit, total)
  val topics = edit?.topics.orEmpty().sortedBy { it.start }
  val marked = found.map { TimelineEntry(it.start, it.end, it.title, true) }
  return (marked + between(found, total).flatMap { split(it, topics) }).sortedBy { it.start }
}

private fun split(
  kept: KeptSpan,
  topics: List<EpisodeChapter>,
): List<TimelineEntry> {
  val opening = EpisodeChapter(kept.start, topics.lastOrNull { it.start <= kept.start + TOPIC_LEAD }?.title ?: PROGRAMME)
  val marks = listOf(opening) + topics.filter { it.start > kept.start + TOPIC_LEAD && it.start < kept.end }
  val ends = marks.drop(1).map { it.start } + kept.end
  return marks.zip(ends).map { (mark, end) -> TimelineEntry(mark.start, end, mark.title, false) }
}
