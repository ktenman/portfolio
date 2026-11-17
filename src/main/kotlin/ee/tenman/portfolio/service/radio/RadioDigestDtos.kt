package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.model.CollectionSchedules
import java.time.Instant
import java.time.LocalDate
import java.time.Period

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
  val earlier: Boolean = false,
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

enum class DigestShow(
  val title: String,
  val directory: String,
  val prefix: String,
  val seconds: Double,
  val cron: String,
  val job: String,
  val period: Period,
  val reach: Period,
  val prompt: String,
) {
  DAY(
    "Day in Radio",
    ".digest-day",
    "day-in-radio-",
    1800.0,
    CollectionSchedules.RADIO_DAY_DIGEST_CRON,
    "RadioDayDigestJob",
    Period.ofDays(1),
    Period.ofDays(2),
    "radio/digest-day-judge-prompt.txt",
  ),
  WEEK(
    "Week in Radio",
    ".digest",
    "week-in-radio-",
    3600.0,
    CollectionSchedules.RADIO_DIGEST_CRON,
    "RadioDigestJob",
    Period.ofWeeks(1),
    Period.ofWeeks(1),
    "radio/digest-judge-prompt.txt",
  ),
}

data class RadioDigest(
  val show: DigestShow,
  val recording: RecordingSummary,
  val stories: List<DigestStory>,
)

enum class DigestStep {
  STORIES,
  CLIPS,
  CUTTING,
  MIXING,
}

data class DigestFailure(
  val reason: String,
  val tries: Int,
  val attempts: Int,
  val nextTry: String?,
)

data class DigestBuild(
  val show: DigestShow,
  val date: LocalDate,
  val step: DigestStep?,
  val titles: List<String> = emptyList(),
  val done: Int = 0,
  val percent: Int = 0,
  val secondsLeft: Int = 0,
  val failure: DigestFailure? = null,
) {
  val title: String
    get() = digestTitle(show, date)
}

data class DigestTrack(
  val build: DigestBuild,
  val startedAt: Instant,
  val stepAt: Instant,
)
