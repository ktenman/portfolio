package ee.tenman.portfolio.service.radio

data class RadioSearch(
  val topics: List<TopicHit>,
  val moreTopics: Int,
  val groups: List<LineGroup>,
  val lines: Int,
  val recordings: Int,
  val partial: Boolean,
  val suggestions: List<Suggestion>,
  val next: String?,
  val searchable: Boolean,
)

data class TopicHit(
  val name: String,
  val programme: String,
  val startedAt: Double,
  val start: Double,
  val title: List<TextPart>,
  val summary: List<TextPart>,
)

data class LineGroup(
  val name: String,
  val programme: String,
  val startedAt: Double,
  val lines: List<LineHit>,
  val more: Int,
)

data class LineHit(
  val start: Double,
  val kept: Boolean,
  val text: List<TextPart>,
)

data class TextPart(
  val text: String,
  val mark: Boolean,
)

data class Suggestion(
  val word: String,
  val close: List<String>,
)

class IndexedRecording(
  val name: String,
  val programme: String,
  val startedAt: Double,
  val lines: List<IndexedLine>,
  val words: List<IndexedWord>,
  val topics: List<IndexedTopic>,
)

data class IndexedLine(
  val start: Double,
  val text: String,
  val kept: Boolean,
)

class IndexedWord(
  val word: String,
  val folded: String,
  val lines: IntArray,
)

data class IndexedTopic(
  val start: Double,
  val title: String,
  val summary: String?,
  val words: Set<String>,
)
