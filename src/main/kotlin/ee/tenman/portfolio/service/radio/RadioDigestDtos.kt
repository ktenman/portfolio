package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.Station
import java.time.LocalDate

data class DigestRow(
  val id: String,
  val recording: String,
  val programme: String,
  val station: Station,
  val day: LocalDate,
  val title: String,
  val summary: String?,
  val start: Double,
  val end: Double,
  val kept: Double,
)

data class JudgedStory(
  val title: String,
  val summary: String,
  val rows: List<String>,
  val best: String,
  val kind: String,
  val importance: Int,
  val interest: Int,
  val repeat: Boolean,
  val reason: String,
)

data class Judgement(
  val stories: List<JudgedStory>,
)

data class RankedStory(
  val story: JudgedStory,
  val best: DigestRow,
  val others: List<DigestRow>,
  val score: Int,
)

data class ClipAnswer(
  val first: Int,
  val last: Int,
)

data class DigestClip(
  val story: RankedStory,
  val recording: RecordingSummary,
  val start: Double,
  val end: Double,
  val lines: List<TranscriptLine>,
) {
  val length: Double
    get() = end - start
}

data class DigestMention(
  val programme: String,
  val station: Station,
  val day: LocalDate,
)

data class SectionSource(
  val recording: String,
  val programme: String,
  val station: Station,
  val day: LocalDate,
  val at: Double,
  val also: List<DigestMention>,
)

data class DigestStory(
  val title: String,
  val summary: String,
  val start: Double,
  val end: Double,
  val importance: Int,
  val interest: Int,
  val score: Int,
  val kind: String,
  val reason: String,
  val source: SectionSource,
)

data class Digest(
  val date: LocalDate,
  val builtAt: String,
  val seconds: Double,
  val model: String,
  val stories: List<DigestStory>,
)

data class RadioDigest(
  val recording: RecordingSummary,
  val stories: List<DigestStory>,
)
