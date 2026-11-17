package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.Station
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val DIGEST_FILE = "digest.json"
const val PAUSE_SECONDS = 1.0
private const val ROW_SECONDS = 60.0
private val DAY_NAME = Regex("""\d{4}-\d{2}-\d{2}""")
private const val SCORE_FLOOR = 8
private const val PER_PROGRAMME = 4
private const val OTHER_PROGRAMMES = 2
private const val FALLBACK_SECONDS = 180.0
private val CLIP_SECONDS = 90.0..240.0
private val WEEK_START = DateTimeFormatter.ofPattern("dd.MM")

fun finished(recording: RecordingSummary): Boolean =
  recording.episodeBytes != null &&
    recording.render == null &&
    recording.failure == null &&
    recording.programmeCuts.all { it.state == ProgrammeCutState.FOUND }

fun rows(
  recording: RecordingSummary,
  zone: ZoneId,
): List<DigestRow> {
  val topics = recording.editList?.topics.orEmpty()
  val ends = topics.drop(1).map { it.start } + recording.status.seconds
  return topics.zip(ends).map { (topic, end) -> row(recording, topic, end, zone) }.filter { it.kept >= ROW_SECONDS }
}

private fun row(
  recording: RecordingSummary,
  topic: EpisodeChapter,
  end: Double,
  zone: ZoneId,
) = DigestRow(
  id = "",
  recording = recording.name,
  programme = programme(recording, topic.start),
  station = recording.status.station,
  day = Instant.ofEpochSecond((recording.status.startedAt + topic.start).toLong()).atZone(zone).toLocalDate(),
  title = topic.title,
  summary = topic.summary,
  start = topic.start,
  end = end,
  kept = overlap(topic.start, end, recording.audible),
)

private fun programme(
  recording: RecordingSummary,
  seconds: Double,
): String =
  recording.editList
    ?.boundaries
    .orEmpty()
    .filter { it.programme != null && it.seconds <= seconds }
    .maxByOrNull { it.seconds }
    ?.programme ?: recording.programme

fun ranked(
  judgement: Judgement,
  rows: Map<String, DigestRow>,
): List<RankedStory> =
  judgement.stories
    .mapNotNull { scored(it, rows) }
    .filter { it.score >= SCORE_FLOOR }
    .sortedWith(
      compareByDescending<RankedStory> { it.score }
        .thenByDescending { it.story.importance }
        .thenByDescending { it.best.recording }
        .thenByDescending { it.best.start },
    ).distinctBy { it.best.id }
    .fold(emptyList(), ::capped)

private fun capped(
  kept: List<RankedStory>,
  story: RankedStory,
): List<RankedStory> = if (kept.count { it.best.programme == story.best.programme } < PER_PROGRAMME) kept + story else kept

private fun scored(
  story: JudgedStory,
  rows: Map<String, DigestRow>,
): RankedStory? {
  if (story.repeat) return null
  val known = story.rows.distinct().mapNotNull { rows[it] }
  val best = known.firstOrNull { it.id == story.best } ?: return null
  val others = known.filter { it.id != best.id }
  val programmes =
    others
      .map { it.programme }
      .filter { it != best.programme }
      .distinct()
      .size
  val score = 2 * story.importance.coerceIn(1, 5) + story.interest.coerceIn(1, 5) + minOf(programmes, OTHER_PROGRAMMES)
  return RankedStory(story, best, others, score)
}

fun clipped(
  answer: ClipAnswer?,
  lines: List<TranscriptLine>,
  row: DigestRow,
  audible: List<KeptSpan>,
): Pair<Double, Double> {
  val spans = audible.map { it.start to it.end }
  val start = episodeSeconds(row.start, spans)
  val end = episodeSeconds(row.end, spans)
  val fallback = start to minOf(start + FALLBACK_SECONDS, end)
  val picked = answer?.let { picked(it, lines, spans) } ?: return fallback
  val (first, last) = picked.first to minOf(picked.second, end)
  return if (first >= start && last - first in CLIP_SECONDS) first to last else fallback
}

private fun picked(
  answer: ClipAnswer,
  lines: List<TranscriptLine>,
  spans: List<Pair<Double, Double>>,
): Pair<Double, Double>? {
  val first = lines.getOrNull(answer.first - 1) ?: return null
  val last = lines.getOrNull(answer.last - 1) ?: return null
  return episodeSeconds(first.start, spans) to episodeSeconds(last.end, spans)
}

fun inStory(
  line: TranscriptLine,
  row: DigestRow,
  audible: List<KeptSpan>,
): Boolean = !line.music && line.start >= row.start && line.start < row.end && audible.any { line.start >= it.start && line.start < it.end }

fun fitted(
  clips: List<DigestClip>,
  seconds: Double,
): List<DigestClip> =
  clips.fold(emptyList()) { kept, clip -> if ((kept + clip).sumOf { it.length + PAUSE_SECONDS } <= seconds) kept + clip else kept }

fun dominant(stories: List<DigestStory>): Station = heard(stories).first()

private fun heard(stories: List<DigestStory>): List<Station> =
  stories
    .groupBy { it.source.station }
    .entries
    .sortedByDescending { (_, told) -> told.sumOf { it.end - it.start } }
    .map { it.key }

private fun stations(stories: List<DigestStory>): List<Station> =
  (heard(stories) + stories.flatMap { story -> story.source.also.map { it.station } }).distinct()

fun shifted(
  lines: List<TranscriptLine>,
  clip: Pair<Double, Double>,
  audible: List<KeptSpan>,
  offset: Double,
): List<TranscriptLine> {
  val spans = audible.map { it.start to it.end }
  val placed = { seconds: Double -> offset + minOf(episodeSeconds(seconds, spans), clip.second) - clip.first }
  return lines
    .filter { episodeSeconds(it.start, spans) in clip.first..<clip.second }
    .map { line ->
      line.copy(
        start = placed(line.start),
        end = placed(line.end),
        words = line.words.map { it.copy(start = placed(it.start), end = placed(it.end)) },
        split = null,
      )
    }
}

fun rawSeconds(
  seconds: Double,
  audible: List<KeptSpan>,
): Double {
  val seams = audible.runningFold(0.0) { at, span -> at + span.end - span.start }
  val index = audible.indices.firstOrNull { seconds < seams[it + 1] } ?: audible.lastIndex
  return audible[index].start + seconds - seams[index]
}

fun source(clip: DigestClip): SectionSource {
  val best = clip.story.best
  val also =
    clip.story.others
      .map { DigestMention(it.programme, it.station, it.day) }
      .distinct()
  val at = rawSeconds(clip.start, clip.recording.audible)
  return SectionSource(
    best.recording,
    best.programme,
    best.station,
    best.day,
    at,
    also - DigestMention(best.programme, best.station, best.day),
  )
}

fun digests(root: Path): List<Path> = directories(root, DAY_NAME, DIGEST_FILE)

fun digestTitle(
  show: DigestShow,
  date: LocalDate,
): String =
  when (show) {
    DigestShow.DAY -> "${show.title} ${date.format(FULL_DATE)}"
    DigestShow.WEEK -> "${show.title} ${date.minusWeeks(1).format(WEEK_START)}–${date.minusDays(1).format(FULL_DATE)}"
  }

fun digestSummary(
  show: DigestShow,
  digest: Digest,
  bytes: Long,
): RecordingSummary {
  val whole = listOf(KeptSpan(0.0, digest.seconds))
  val timeline = digest.stories.map { TimelineEntry(it.start, it.end, it.title, false, summary = it.summary) }
  return RecordingSummary(
    name = "${show.prefix}${digest.date}",
    programme =
      when (show) {
        DigestShow.DAY -> show.title
        DigestShow.WEEK -> digestTitle(show, digest.date)
      },
    status = status(show, digest),
    sizeBytes = 0,
    transcribed = true,
    speech = null,
    sounds = null,
    editList = edit(digest, whole),
    failure = null,
    episodeBytes = bytes,
    cuts = emptyList(),
    timeline = timeline,
    audible = whole,
    format = null,
    programmeCuts = emptyList(),
    stations = stations(digest.stories),
    shows = digest.stories.map { it.source.programme }.distinct(),
  )
}

private fun status(
  show: DigestShow,
  digest: Digest,
) = RecordingStatus(
    RecordingState.CAPTURED,
    Instant.parse(digest.builtAt).epochSecond.toDouble(),
    digest.seconds,
    programmes = listOf(show.title),
    station = dominant(digest.stories),
  )

private fun edit(
  digest: Digest,
  whole: List<KeptSpan>,
): EditList {
  val chapters = digest.stories.map { EpisodeChapter(it.start, it.title, summary = it.summary) }
  return EditList(
    keep = whole,
    durationSeconds = digest.seconds,
    parts = emptyList(),
    boundaries = emptyList(),
    hosts = emptyList(),
    outline = OutlineSummary(digest.model, 1, 1, 1),
    chapters = chapters,
    reviewed = true,
    renderedAt = digest.builtAt,
    topics = chapters,
  )
}
