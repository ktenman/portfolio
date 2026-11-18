package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.model.CollectionSchedules
import ee.tenman.portfolio.repository.JobExecutionRepository
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

@Service
@OptIn(ExperimentalPathApi::class)
class RadioDigestService(
  private val radioArchiveService: RadioArchiveService,
  private val radioDigestModelService: RadioDigestModelService,
  private val radioRenderService: RadioRenderService,
  private val radioDigestProgressService: RadioDigestProgressService,
  private val radioProperties: RadioProperties,
  private val jobExecutionRepository: JobExecutionRepository,
  private val clock: Clock,
) {
  fun due(
    show: DigestShow,
    date: LocalDate,
  ): Boolean = radioDigestProgressService.current() == null && !built(show, date) && failures(show, date) < ATTEMPTS

  fun build(
    show: DigestShow,
    date: LocalDate,
  ) {
    if (!due(show, date)) return
    val (recordings, rows) = ready(show) ?: return
    radioDigestProgressService.started(show, date)
    runCatching { make(show, date, recordings, rows) }.also { radioDigestProgressService.ended() }.getOrThrow()
  }

  fun rebuild(
    show: DigestShow,
    date: LocalDate,
  ) {
    val (recordings, rows) = checkNotNull(ready(show)) { "No recordings are ready for the $date radio digest" }
    make(show, date, recordings, rows)
  }

  fun building(): DigestBuild? =
    radioDigestProgressService.current()
      ?: radioDigestProgressService.held()
      ?: DigestShow.entries.firstNotNullOfOrNull { failed(it, LocalDate.now(clock)) }

  private fun make(
    show: DigestShow,
    date: LocalDate,
    recordings: List<RecordingSummary>,
    rows: List<DigestRow>,
  ) {
    val judgement =
      checkNotNull(radioDigestModelService.judge(show, rows, told(show, date))) { "The model gave no judgement for the $date radio digest" }
    val ranked = ranked(judgement, rows.associateBy { it.id })
    radioDigestProgressService.judged(ranked.map { it.story.title })
    val clips = fitted(clips(ranked, recordings.associateBy { it.name }), show.seconds)
    check(clips.isNotEmpty() || !built(show, date)) { "No story made the $date radio digest" }
    radioDigestProgressService.fitted(clips.map { it.story.story.title })
    val work = root(show).resolve(BUILD).also { it.deleteRecursively() }
    write(Files.createDirectories(work), digestTitle(show, date), date, clips)
    replace(show, date, work)
  }

  private fun replace(
    show: DigestShow,
    date: LocalDate,
    work: Path,
  ) {
    target(show, date).deleteRecursively()
    Files.move(work, target(show, date), StandardCopyOption.ATOMIC_MOVE)
    digests(root(show)).drop(KEPT).forEach { it.deleteRecursively() }
  }

  private fun failed(
    show: DigestShow,
    date: LocalDate,
  ): DigestBuild? {
    if (built(show, date)) return null
    val tries = failures(show, date)
    if (tries == 0L) return null
    val last = jobExecutionRepository.findFirstByJobNameAndStatusOrderByStartTimeDesc(show.job, JobStatus.FAILURE) ?: return null
    val reason = firstLine(last.message, UNKNOWN)
    return DigestBuild(show, date, null, failure = DigestFailure(reason, tries.toInt(), ATTEMPTS, next(show, tries)))
  }

  private fun next(
    show: DigestShow,
    tries: Long,
  ): String? {
    if (tries >= ATTEMPTS) return null
    val now = clock.instant().atZone(CRON_ZONE)
    return NEXT_TRIES
      .getValue(show)
      .next(now)
      ?.takeIf { it.toLocalDate() == now.toLocalDate() }
      ?.toInstant()
      ?.toString()
  }

  private fun built(
    show: DigestShow,
    date: LocalDate,
  ): Boolean = Files.exists(target(show, date).resolve(DIGEST_FILE))

  private fun failures(
    show: DigestShow,
    date: LocalDate,
  ): Long =
    jobExecutionRepository.countByJobNameAndStatusAndStartTimeAfter(show.job, JobStatus.FAILURE, date.atStartOfDay(clock.zone).toInstant())

  private fun ready(show: DigestShow): Pair<List<RecordingSummary>, List<DigestRow>>? {
    val recordings = gathered(show)
    val rows = marked(show, recordings)
    return (recordings to rows).takeUnless { rows.all { it.earlier } }
  }

  private fun gathered(show: DigestShow): List<RecordingSummary> {
    val now = clock.instant()
    val since = maxOf(now.minus(show.reach), now.minus(Duration.ofDays(radioProperties.localDays).minusHours(1))).epochSecond
    return radioArchiveService
      .recordings()
      .filter { it.span.end > since && finished(it) && Files.exists(episode(it.name)) }
      .sortedBy { it.status.startedAt }
  }

  private fun marked(
    show: DigestShow,
    recordings: List<RecordingSummary>,
  ): List<DigestRow> {
    val news = clock.instant().minus(show.period).epochSecond
    return recordings
      .flatMap { recording -> rows(recording, clock.zone).map { it.copy(earlier = recording.span.end <= news) } }
      .mapIndexed { index, row -> row.copy(id = "r${index + 1}") }
  }

  private fun told(
    show: DigestShow,
    date: LocalDate,
  ): List<Pair<LocalDate, String>> =
    digests(root(show))
      .take(KEPT)
      .mapNotNull { read<Digest>(it.resolve(DIGEST_FILE)) }
      .filter { it.date <= date.minus(show.period) }
      .flatMap { digest -> digest.stories.map { digest.date to it.title } }

  private fun clips(
    ranked: List<RankedStory>,
    recordings: Map<String, RecordingSummary>,
  ): List<DigestClip> =
    ranked
      .groupBy { it.best.recording }
      .flatMap { (name, stories) -> clips(stories, recordings.getValue(name)) }
      .sortedBy { ranked.indexOf(it.story) }

  private fun clips(
    stories: List<RankedStory>,
    recording: RecordingSummary,
  ): List<DigestClip> {
    val all = radioArchiveService.transcript(recording.name).orEmpty()
    return stories.map { story ->
      val lines = all.filter { inStory(it, story.best, recording.audible) }
      val answer = radioDigestModelService.clip(story.story, lines).also { radioDigestProgressService.chosen() }
      val (start, end) = clipped(answer, lines, story.best, recording.audible)
      DigestClip(story, recording, start, end, lines)
    }
  }

  private fun write(
    work: Path,
    title: String,
    date: LocalDate,
    clips: List<DigestClip>,
  ) {
    val offsets = clips.runningFold(0.0) { at, clip -> at + clip.length + PAUSE_SECONDS }
    val stories = clips.mapIndexed { index, clip -> story(clip, offsets[index]) }
    if (clips.isNotEmpty()) {
      render(work, title, clips, offsets, dominant(stories))
      save(work.resolve(TRANSCRIPT_FILE), clips.zip(stories, ::section))
    }
    save(work.resolve(DIGEST_FILE), Digest(date, clock.instant().toString(), offsets.last(), radioProperties.outlineModel.modelId, stories))
  }

  private fun render(
    work: Path,
    title: String,
    clips: List<DigestClip>,
    offsets: List<Double>,
    station: Station,
  ) {
    val spans = cut(work, clips, offsets)
    val marks = clips.mapIndexed { index, clip -> offsets[index] to clip.story.story.title }
    val target = work.resolve(EPISODE_FILE)
    radioRenderService.render(spans, spans.map { it.start to it.end }, marks, title, target, station, radioDigestProgressService::mixed)
    spans.map { it.audio }.distinct().forEach(Files::delete)
  }

  private fun cut(
    work: Path,
    clips: List<DigestClip>,
    offsets: List<Double>,
  ): List<Span> {
    val pause = work.resolve("pause.m4a").also { radioRenderService.silence(it, PAUSE_SECONDS) }
    return clips.flatMapIndexed { index, clip ->
      val audio = work.resolve("clip-${index + 1}.m4a")
      radioRenderService.clip(episode(clip.recording.name), clip.start, clip.end, audio)
      radioDigestProgressService.cut()
      val end = offsets[index] + clip.length
      listOf(Span(offsets[index], end, audio, offsets[index], audio), Span(end, offsets[index + 1], pause, end, pause))
    }
  }

  private fun story(
    clip: DigestClip,
    offset: Double,
  ): DigestStory {
    val judged = clip.story.story
    return DigestStory(
      judged.title,
      judged.summary,
      offset,
      offset + clip.length,
      judged.importance,
      judged.interest,
      clip.story.score,
      judged.kind,
      judged.reason,
      source(clip),
    )
  }

  private fun section(
    clip: DigestClip,
    story: DigestStory,
  ): TranscriptSection {
    val lines = shifted(clip.lines, clip.start to clip.end, clip.recording.audible, story.start)
    return TranscriptSection(story.start, story.end, story.title, null, story.start, lines, story.source)
  }

  private fun target(
    show: DigestShow,
    date: LocalDate,
  ): Path = root(show).resolve(date.toString())

  private fun root(show: DigestShow): Path = radioProperties.recordings.resolve(show.directory)

  private fun episode(name: String): Path = radioProperties.recordings.resolve(name).resolve(EPISODE_FILE)

  companion object {
    private const val KEPT = 7
    private const val ATTEMPTS = 3
    private const val BUILD = "build.tmp"
    private const val UNKNOWN = "The digest build failed"
    private val CRON_ZONE = ZoneId.of(CollectionSchedules.TIME_ZONE)
    private val NEXT_TRIES = DigestShow.entries.associateWith { CronExpression.parse(it.cron) }
  }
}
