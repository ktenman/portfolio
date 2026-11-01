package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.Station

data class RadioSearch(
  val topics: List<TopicHit> = emptyList(),
  val moreTopics: Int = 0,
  val groups: List<LineGroup> = emptyList(),
  val lines: Int = 0,
  val recordings: Int = 0,
  val partial: Boolean = false,
  val suggestions: List<String> = emptyList(),
  val next: String? = null,
  val searchable: Boolean,
)

data class TopicHit(
  val name: String,
  val programme: String,
  val startedAt: Double,
  val start: Double,
  val title: List<TextPart>,
  val summary: List<TextPart>,
  val station: Station,
)

data class LineGroup(
  val name: String,
  val programme: String,
  val startedAt: Double,
  val lines: List<LineHit>,
  val more: Int,
  val station: Station,
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

class IndexedRecording(
  val name: String,
  val programme: String,
  val startedAt: Double,
  val lines: List<IndexedLine>,
  val words: List<IndexedWord>,
  val topics: List<IndexedTopic>,
  val station: Station,
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
