package ee.tenman.portfolio.service.radio

import java.text.Normalizer

private const val PAGE = 10
private const val SHOWN = 4
private const val TOPICS = 5
private const val LINE_ROOM = 240
private const val SUMMARY_ROOM = 200
private const val LEAD = 60
private const val PREFIX = 4
private const val EXACT = 0.5
private const val CLOSE = 0.34
private const val SUGGESTED = 3
private const val MAX_TERMS = 10
private val WORD = Regex("[\\p{L}\\p{N}]+")
private val MARKS = Regex("\\p{M}+")
private val TWO_CAPITALS = Regex("\\p{Lu}{2}")
private val STOP =
  (
    "aga ainult ehk ei et ja juba ka kas kes kui mida mis nad nii ning oli oma on see seda selle siis ta tema veel või voi " +
      "about after all also and any are can did does for from had has have her him his how into its our said say she should than " +
      "that the their them then there they this was were what when where which who why will with would you your news"
  ).split(" ").toSet()
private val ALIASES =
  mapOf(
    "anthropic" to listOf("antropik", "antroopik", "antropic"),
    "tehisaru" to listOf("tehisharu"),
    "tehisharu" to listOf("tehisaru"),
    "vseviov" to listOf("vsevjev"),
  )

private class Hit(
  val recording: IndexedRecording,
  val line: Int,
  val score: Double,
  val matched: Int,
)

private class Close(
  val word: String,
  val lines: Int,
  val score: Double,
)

private class Place(
  val score: Double,
  val startedAt: Double,
  val name: String,
) {
  override fun toString() = "$score $startedAt $name"
}

private val ORDER = compareByDescending(Place::score).thenByDescending(Place::startedAt).thenBy(Place::name)

fun indexed(
  name: String,
  status: RecordingStatus,
  transcript: Transcript,
  edit: EditList?,
): IndexedRecording {
  val cuts = cuts(edit, status.seconds)
  val lines =
    mark(music(lines(transcript), edit), cuts)
      .filterNot { it.music }
      .map { IndexedLine(it.start, it.text.trim(), it.split == null && kept(it.start, edit)) }
  val words =
    lines
      .flatMapIndexed { index, line -> WORD.findAll(line.text).map { it.value.lowercase() to index }.toList() }
      .groupBy({ it.first }, { it.second })
      .map { (word, found) -> IndexedWord(word, fold(word), found.distinct().toIntArray()) }
  val about = listOf(status.title()) + edit?.hosts.orEmpty()
  val topics =
    timeline(cuts, edit?.topics.orEmpty(), status.seconds)
      .filterNot { it.cut }
      .distinctBy { it.title to it.summary }
      .mapNotNull { entry ->
        entry.title?.let { IndexedTopic(entry.start, it, entry.summary, folded(listOfNotNull(it, entry.summary) + about)) }
      }
  return IndexedRecording(name, status.title(), status.startedAt, lines, words, topics)
}

fun search(
  recordings: List<IndexedRecording>,
  query: String,
  after: String?,
): RadioSearch {
  val words = WORD.findAll(Normalizer.normalize(query, Normalizer.Form.NFC)).map { it.value }.toList()
  val terms = terms(words)
  if (terms.isEmpty()) return RadioSearch(searchable = false)
  val typed = words.map { it.lowercase() }.filter { fold(it) in terms }.toSet()
  val hits = recordings.flatMap { hits(it, terms, typed) }
  val complete = hits.any { it.matched == terms.size }
  val visible = hits.filter { if (complete) it.matched == terms.size else it.matched * 2 >= terms.size }
  val groups =
    visible
      .groupBy { it.recording }
      .toList()
      .sortedWith(compareBy(ORDER, ::place))
  val rest = following(groups, after)
  val shown = rest.take(PAGE)
  val topics = if (after == null) topics(recordings, terms) else emptyList()
  return RadioSearch(
    topics = topics.take(TOPICS).map { (_, recording, entry) -> topic(recording, entry, terms) },
    moreTopics = (topics.size - TOPICS).coerceAtLeast(0),
    groups = shown.map { (recording, found) -> group(recording, found, terms) },
    lines = visible.size,
    recordings = groups.size,
    partial = !complete && visible.isNotEmpty(),
    suggestions = if (visible.isEmpty() && terms.size <= 2) suggestions(recordings, terms) else emptyList(),
    next = if (rest.size > PAGE) place(shown.last()).toString() else null,
    searchable = true,
  )
}

private fun following(
  groups: List<Pair<IndexedRecording, List<Hit>>>,
  after: String?,
): List<Pair<IndexedRecording, List<Hit>>> {
  if (after == null) return groups
  val last = cursor(after) ?: return emptyList()
  return groups.filter { ORDER.compare(place(it), last) > 0 }
}

private fun place(group: Pair<IndexedRecording, List<Hit>>): Place =
  Place(group.second.maxOf(Hit::score), group.first.startedAt, group.first.name)

private fun cursor(text: String): Place? {
  val (score, startedAt, name) = text.split(" ", limit = 3).takeIf { it.size == 3 } ?: return null
  return Place(score.toDoubleOrNull() ?: return null, startedAt.toDoubleOrNull() ?: return null, name)
}

private fun kept(
  start: Double,
  edit: EditList?,
): Boolean = edit == null || edit.keep.any { start >= it.start && start < it.end }

private fun fold(text: String): String = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(MARKS, "")

private fun folded(texts: List<String>): Set<String> = texts.flatMap { text -> WORD.findAll(text).map { fold(it.value) }.toList() }.toSet()

private fun terms(words: List<String>): List<String> =
  words
    .filter { it.length > 2 || TWO_CAPITALS.matches(it) }
    .filterNot { it.lowercase() in STOP }
    .map(::fold)
    .distinct()
    .take(MAX_TERMS)

private fun tier(
  term: String,
  word: String,
): Int =
  when {
    word == term -> 3
    term.length >= PREFIX && word.startsWith(term) -> 2
    ALIASES[term].orEmpty().any { word.startsWith(it) } -> 1
    else -> 0
  }

private fun lit(
  word: String,
  terms: List<String>,
): Boolean = fold(word).let { folded -> terms.any { tier(it, folded) > 0 } }

private fun hits(
  recording: IndexedRecording,
  terms: List<String>,
  typed: Set<String>,
): List<Hit> {
  val ranks = terms.map { best(recording, it) }
  val exact =
    recording.words
    .filter { it.word in typed }
    .flatMap { it.lines.asIterable() }
    .toSet()
  return ranks.flatMap { it.keys }.distinct().map { line ->
    val found = ranks.mapNotNull { it[line] }
    Hit(recording, line, found.sum() + if (line in exact) EXACT else 0.0, found.size)
  }
}

private fun best(
  recording: IndexedRecording,
  term: String,
): Map<Int, Int> {
  val best = HashMap<Int, Int>()
  recording.words.forEach { word ->
    val rank = tier(term, word.folded)
    if (rank > 0) word.lines.forEach { best.merge(it, rank, ::maxOf) }
  }
  return best
}

private fun group(
  recording: IndexedRecording,
  hits: List<Hit>,
  terms: List<String>,
): LineGroup {
  val shown = hits.sortedWith(compareByDescending(Hit::score).thenBy(Hit::line)).take(SHOWN).sortedBy(Hit::line)
  val lines = shown.map { recording.lines[it.line] }.map { LineHit(it.start, it.kept, snippet(it.text, terms, LINE_ROOM)) }
  return LineGroup(recording.name, recording.programme, recording.startedAt, lines, hits.size - shown.size)
}

private fun topics(
  recordings: List<IndexedRecording>,
  terms: List<String>,
): List<Triple<Int, IndexedRecording, IndexedTopic>> =
  recordings
    .flatMap { recording ->
      recording.topics.mapNotNull { topic ->
        val ranks = terms.map { term -> topic.words.maxOfOrNull { tier(term, it) } ?: 0 }
        if (ranks.any { it == 0 }) return@mapNotNull null
        Triple(ranks.sum(), recording, topic)
      }
    }.sortedWith(
      compareByDescending<Triple<Int, IndexedRecording, IndexedTopic>> { it.first }
        .thenByDescending { it.second.startedAt }
        .thenBy { it.third.start },
    )

private fun topic(
  recording: IndexedRecording,
  topic: IndexedTopic,
  terms: List<String>,
): TopicHit =
  TopicHit(
    recording.name,
    recording.programme,
    recording.startedAt,
    topic.start,
    marked(topic.title, terms),
    snippet(topic.summary.orEmpty(), terms, SUMMARY_ROOM),
  )

private fun suggestions(
  recordings: List<IndexedRecording>,
  terms: List<String>,
): List<String> {
  val vocabulary = recordings.flatMap { it.words }.groupBy { it.folded }
  return terms
    .flatMap { term ->
      val wanted = grams(term)
      vocabulary
        .filterKeys { it.length >= PREFIX && it !in STOP }
        .map { (folded, words) -> Close(words.first().word, words.sumOf { it.lines.size }, similarity(wanted, grams(folded))) }
        .filter { it.score >= CLOSE }
        .sortedWith(compareByDescending(Close::score).thenByDescending(Close::lines))
        .take(SUGGESTED)
        .map { it.word }
    }.distinct()
}

private fun grams(word: String): Set<String> = " $word ".windowed(3).toSet()

private fun similarity(
  one: Set<String>,
  other: Set<String>,
): Double {
  val shared = if (one.size < other.size) one.count { it in other } else other.count { it in one }
  return shared.toDouble() / (one.size + other.size - shared)
}

private fun snippet(
  text: String,
  terms: List<String>,
  room: Int,
): List<TextPart> {
  if (text.length <= room) return marked(text, terms)
  val at =
    WORD
    .findAll(text)
    .firstOrNull { lit(it.value, terms) }
    ?.range
    ?.first ?: 0
  val near = (at - LEAD).coerceIn(0, text.length - room)
  val end = near + room
  val from = if (near == 0) 0 else text.indexOf(' ', near).takeIf { it in near until at }?.plus(1) ?: near
  val to = if (end == text.length) end else text.lastIndexOf(' ', end).takeIf { it > from } ?: end
  val head = if (from > 0) "… " else ""
  val tail = if (to < text.length) " …" else ""
  return marked(head + text.substring(from, to) + tail, terms)
}

private fun marked(
  text: String,
  terms: List<String>,
): List<TextPart> {
  val found =
    WORD
    .findAll(text)
    .filter { lit(it.value, terms) }
    .map { it.range }
    .toList()
  val edges = listOf(0) + found.flatMap { listOf(it.first, it.last + 1) } + text.length
  return edges.zipWithNext().mapIndexedNotNull { index, (from, to) ->
    TextPart(text.substring(from, to), index % 2 == 1).takeIf { from < to }
  }
}
