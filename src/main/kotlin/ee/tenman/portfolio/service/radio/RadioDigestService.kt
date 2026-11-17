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
import java.time.format.DateTimeFormatter
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
  fun due(date: LocalDate): Boolean = !built(date) && failures(date) < ATTEMPTS

  fun build(date: LocalDate) {
    if (!due(date)) return
    val recordings = gathered()
    val rows = recordings.flatMap { rows(it, clock.zone) }.mapIndexed { index, row -> row.copy(id = "r${index + 1}") }
    if (rows.isEmpty()) return
    radioDigestProgressService.started(date)
    runCatching { make(date, recordings, rows) }.also { radioDigestProgressService.ended() }.getOrThrow()
  }

  fun building(): DigestBuild? = radioDigestProgressService.current() ?: failed(LocalDate.now(clock))

  private fun make(
    date: LocalDate,
    recordings: List<RecordingSummary>,
    rows: List<DigestRow>,
  ) {
    val judgement =
      checkNotNull(radioDigestModelService.judge(rows, told())) { "The model gave no judgement for the $date radio digest" }
    val ranked = ranked(judgement, rows.associateBy { it.id })
    radioDigestProgressService.judged(ranked.map { it.story.title })
    val clips = fitted(clips(ranked, recordings.associateBy { it.name }))
    radioDigestProgressService.fitted(clips.map { it.story.story.title })
    val work = root().resolve(BUILD).also { it.deleteRecursively() }
    write(Files.createDirectories(work), date, clips)
    Files.move(work, target(date), StandardCopyOption.ATOMIC_MOVE)
    digests(root()).drop(KEPT).forEach { it.deleteRecursively() }
  }

  private fun failed(date: LocalDate): DigestBuild? {
    if (built(date)) return null
    val tries = failures(date)
    if (tries == 0L) return null
    val last = jobExecutionRepository.findFirstByJobNameAndStatusOrderByStartTimeDesc(JOB, JobStatus.FAILURE) ?: return null
    val line = last.message.orEmpty().substringBefore('\n')
    val reason = line.trim().ifBlank { UNKNOWN }
    return DigestBuild(date, null, failure = DigestFailure(reason, tries.toInt(), ATTEMPTS, next(tries)))
  }

  private fun next(tries: Long): String? {
    if (tries >= ATTEMPTS) return null
    return NEXT_TRY.next(clock.instant().atZone(CRON_ZONE))?.toInstant()?.toString()
  }

  private fun built(date: LocalDate): Boolean = Files.exists(target(date).resolve(DIGEST_FILE))

  private fun failures(date: LocalDate): Long =
    jobExecutionRepository.countByJobNameAndStatusAndStartTimeAfter(JOB, JobStatus.FAILURE, date.atStartOfDay(clock.zone).toInstant())

  private fun gathered(): List<RecordingSummary> {
    val since = clock.instant().minus(Duration.ofDays(radioProperties.localDays).minusHours(1)).epochSecond
    return radioArchiveService
      .recordings()
      .filter { it.status.startedAt + it.status.seconds > since && finished(it) && Files.exists(episode(it.name)) }
      .sortedBy { it.status.startedAt }
  }

  private fun told(): List<Pair<LocalDate, String>> =
    digests(root()).take(KEPT).flatMap { folder ->
      read<Digest>(folder.resolve(DIGEST_FILE))?.let { digest -> digest.stories.map { digest.date to it.title } }.orEmpty()
    }

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
    date: LocalDate,
    clips: List<DigestClip>,
  ) {
    val offsets = clips.runningFold(0.0) { at, clip -> at + clip.length + PAUSE_SECONDS }
    val stories = clips.mapIndexed { index, clip -> story(clip, offsets[index]) }
    if (clips.isNotEmpty()) {
      render(work, date, clips, offsets, dominant(stories))
      save(work.resolve(TRANSCRIPT_FILE), clips.zip(stories, ::section))
    }
    save(work.resolve(DIGEST_FILE), Digest(date, clock.instant().toString(), offsets.last(), radioProperties.outlineModel.modelId, stories))
  }

  private fun render(
    work: Path,
    date: LocalDate,
    clips: List<DigestClip>,
    offsets: List<Double>,
    station: Station,
  ) {
    val spans = cut(work, clips, offsets)
    val marks = clips.mapIndexed { index, clip -> offsets[index] to clip.story.story.title }
    val title = "$DIGEST_TITLE ${date.format(TITLE)}"
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

  private fun target(date: LocalDate): Path = root().resolve(date.toString())

  private fun root(): Path = radioProperties.recordings.resolve(DIGEST_DIRECTORY)

  private fun episode(name: String): Path = radioProperties.recordings.resolve(name).resolve(EPISODE_FILE)

  companion object {
    private const val KEPT = 7
    private const val ATTEMPTS = 3
    private const val BUILD = "build.tmp"
    private const val JOB = "RadioDigestJob"
    private const val UNKNOWN = "The digest build failed"
    private val TITLE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val NEXT_TRY = CronExpression.parse(CollectionSchedules.RADIO_DIGEST_CRON)
    private val CRON_ZONE = ZoneId.of(CollectionSchedules.TIME_ZONE)
  }
}
