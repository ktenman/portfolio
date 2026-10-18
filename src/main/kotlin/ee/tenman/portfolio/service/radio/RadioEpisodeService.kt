package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.repository.RadioAiringRepository
import ee.tenman.portfolio.repository.RadioProgrammeRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.min

@Service
class RadioEpisodeService(
  private val radioOutlineService: RadioOutlineService,
  private val radioCutService: RadioCutService,
  private val radioRenderService: RadioRenderService,
  private val radioAiringRepository: RadioAiringRepository,
  private val radioProgrammeRepository: RadioProgrammeRepository,
  private val radioProperties: RadioProperties,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun make(directory: Path) {
    val todo = todo(directory)
    if (todo.isEmpty()) return
    runCatching { cut(directory, todo) }.onFailure { fail(directory, it) }
  }

  private fun ready(directory: Path): Boolean {
    val files =
      Files.notExists(directory.resolve(FAILED)) && listOf(STATUS_FILE, TRANSCRIPT_FILE).all { Files.exists(directory.resolve(it)) }
    return files && readStatus(directory).state in READY
  }

  fun todo(directory: Path): List<String> {
    if (!ready(directory)) return emptyList()
    val status = readStatus(directory)
    val requested = read<CutRequest>(directory.resolve(CUT_REQUEST))?.programmes.orEmpty()
    val todo = wanted(status, requested) - read<EditList>(directory.resolve(EDIT_LIST)).found()
    if (todo.isEmpty() || Files.isWritable(directory)) return todo
    log.warn("Cannot write the radio episode into $directory")
    return emptyList()
  }

  private fun cut(
    directory: Path,
    todo: List<String>,
  ) {
    val status = readStatus(directory)
    val timeline = Timeline(directory, status.parts)
    val edit = edited(read(directory.resolve(EDIT_LIST)), findings(directory, status, timeline, todo), status, timeline)
    if (todo.any { it != PROGRAMME }) return save(directory.resolve(EDIT_LIST), edit)
    val temporary = directory.resolve(TEMPORARY)
    val marks = edit.chapters.map { it.start to it.title }
    radioRenderService.render(timeline, edit.keep.map { it.start to it.end }, marks, title(status, edit.found()), temporary)
    save(directory.resolve(EDIT_LIST), edit)
    Files.move(temporary, directory.resolve(EPISODE), StandardCopyOption.ATOMIC_MOVE)
  }

  private fun findings(
    directory: Path,
    status: RecordingStatus,
    timeline: Timeline,
    todo: List<String>,
  ): List<Finding> {
    val lines = lines(readTranscript(directory))
    val db = loudness(timeline)
    val slots = slots(status)
    return todo.map { find(directory, status, show(status, it, slots[it]), lines, timeline, db) }
  }

  private fun edited(
    previous: EditList?,
    findings: List<Finding>,
    status: RecordingStatus,
    timeline: Timeline,
  ): EditList {
    val placed =
      merged(previous?.boundaries.orEmpty().map { it.copy(programme = it.programme ?: PROGRAMME) }, findings.flatMap { it.placed })
    val keep = placed.map { it.seconds }.chunked(2).map { it[0] to it[1] }
    val kept = audible(timeline.spans, keep)
    val topics = topics(previous, findings)
    val marks = chapters(kept, topics, names(status, placed.mapNotNull { it.programme }.toSet()))
    return editList(previous, findings, status, placed, keep, kept, marks, topics)
  }

  private fun topics(
    previous: EditList?,
    findings: List<Finding>,
  ): List<OutlineChapter> =
    (previous?.topics.orEmpty().map { OutlineChapter(it.start, it.title) } + findings.flatMap { it.agreed.chapters }).sortedBy { it.time }

  private fun find(
    directory: Path,
    status: RecordingStatus,
    show: Show,
    lines: List<TranscriptLine>,
    timeline: Timeline,
    db: DoubleArray,
  ): Finding {
    val saved = directory.resolve(outline(status, show.name))
    val (reads, model) = reads(saved, show) { describe(status, show, lines, events(db)) }
    val matched = reads.filterNotNull().mapNotNull { match(it, lines) }
    check(matched.isNotEmpty()) { "No outline read in $saved matches the transcript" }
    val agreed = consensus(matched)
    val placed = radioCutService.boundaries(timeline, db, agreed, lines, library(radioProperties.jingles))
    return Finding(agreed, placed.map { it.copy(programme = show.name) }, OutlineSummary(model, reads.size, matched.size, agreed.voters))
  }

  private fun outline(
    status: RecordingStatus,
    programme: String,
  ): String = status.programmes.indexOf(programme).let { if (it == 0) OUTLINE else "outline-${it + 1}.json" }

  private fun slots(status: RecordingStatus): Map<String, Slot> {
    val from = Instant.ofEpochSecond(status.startedAt.toLong())
    val airings = radioAiringRepository.findStarting(from, from.plusSeconds(status.seconds.toLong()))
    val names = radioProgrammeRepository.findAllById(airings.map { it.programmeId }.toSet()).associate { it.id to it.name }
    val slots = airings.map { names.getValue(it.programmeId) to Slot(it.startsAt.epochSecond, it.endsAt.epochSecond) }
    return slots.distinctBy { it.first }.toMap()
  }

  private fun merged(
    previous: List<Boundary>,
    found: List<Boundary>,
  ): List<Boundary> {
    val shows = (previous + found).groupBy { it.programme }.values.sortedBy { it.first().seconds }
    check(shows.zipWithNext().all { (a, b) -> a.last().seconds < b.first().seconds }) {
      "Programmes overlap in the recording: ${shows.map { "${it.first().programme} ${it.first().seconds}-${it.last().seconds} s" }}"
    }
    return shows.flatten()
  }

  private fun reads(
    saved: Path,
    show: Show,
    text: () -> String,
  ): Pair<List<OutlineRead?>, String?> {
    if (Files.exists(saved)) return JsonMapperFactory.instance.readValue<List<OutlineRead?>>(Files.readString(saved)) to null
    val model = radioProperties.outlineModel
    val reads = radioOutlineService.reads(show, text(), model)
    check(reads.any { it != null }) { "No outline read came back from ${model.modelId} for $saved" }
    save(saved, reads)
    return reads to model.modelId
  }

  private fun editList(
    previous: EditList?,
    findings: List<Finding>,
    status: RecordingStatus,
    placed: List<Boundary>,
    keep: List<Pair<Double, Double>>,
    kept: List<Pair<Double, Double>>,
    marks: List<Pair<Double, String>>,
    topics: List<OutlineChapter>,
  ) = EditList(
    keep = keep.map { KeptSpan(it.first, it.second) },
    durationSeconds = rounded(kept.sumOf { it.second - it.first }),
    parts = status.parts,
    boundaries = placed,
    hosts = (previous?.hosts.orEmpty() + findings.flatMap { it.agreed.hosts }).distinct(),
    outline = findings.last().summary,
    chapters = marks.map { EpisodeChapter(rounded(it.first), it.second) },
    reviewed = false,
    renderedAt = now(),
    topics = topics.map { EpisodeChapter(rounded(it.time), it.title) },
    music = previous?.music.orEmpty() + findings.mapNotNull { music(it) },
    manual = previous?.manual.orEmpty(),
  )

  private fun music(finding: Finding): KeptSpan? =
    KeptSpan(finding.placed.first().seconds, finding.agreed.firstSpeechTime).takeIf { finding.agreed.opensWithMusic }

  @Synchronized
  fun review(
    directory: Path,
    keep: List<KeptSpan>,
  ): EditList? {
    val saved = directory.resolve(EDIT_LIST).takeIf(Files::exists) ?: return null
    val reviewed = rerender(directory, JsonMapperFactory.instance.readValue<EditList>(Files.readString(saved)), keep)
    save(saved, reviewed)
    return reviewed
  }

  private fun rerender(
    directory: Path,
    edit: EditList,
    keep: List<KeptSpan>,
  ): EditList {
    val status = readStatus(directory)
    val timeline = Timeline(directory, status.parts)
    val spans = spans(timeline, keep)
    val kept = audible(timeline.spans, spans)
    val marks = chapters(kept, edit.topics.map { OutlineChapter(it.start, it.title) }, names(status, edit.found()))
    val temporary = directory.resolve(REVIEWED)
    runCatching { radioRenderService.render(timeline, spans, marks, title(status, edit.found()), temporary) }
      .onFailure { Files.deleteIfExists(temporary) }
      .getOrThrow()
    Files.move(temporary, directory.resolve(EPISODE), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    return reviewed(edit, spans.map { KeptSpan(it.first, it.second) }, kept, marks)
  }

  private fun spans(
    timeline: Timeline,
    keep: List<KeptSpan>,
  ): List<Pair<Double, Double>> {
    val spans = keep.map { it.start to min(it.end, timeline.seconds) }
    require(ordered(spans) && spans.all { audible(timeline.spans, listOf(it)).isNotEmpty() }) {
      "Keep list $keep is not ordered audible spans within 0-${timeline.seconds} s"
    }
    return spans
  }

  private fun reviewed(
    edit: EditList,
    keep: List<KeptSpan>,
    kept: List<Pair<Double, Double>>,
    marks: List<Pair<Double, String>>,
  ) = edit.copy(
    keep = keep,
    durationSeconds = rounded(kept.sumOf { it.second - it.first }),
    chapters = marks.map { EpisodeChapter(rounded(it.first), it.second) },
    reviewed = true,
    renderedAt = now(),
  )

  private fun ordered(spans: List<Pair<Double, Double>>): Boolean =
    spans.isNotEmpty() &&
      spans.all { (start, end) -> start >= 0 && start < end } &&
      spans.zipWithNext().all { (left, right) -> left.second < right.first }

  private fun names(
    status: RecordingStatus,
    done: Set<String>,
  ): String {
    val shown = status.programmes.filter { it in done }
    return shown.ifEmpty { done.toList() }.joinToString(", ")
  }

  private fun title(
    status: RecordingStatus,
    done: Set<String>,
  ): String = "${names(status, done)} ${Instant.ofEpochMilli((status.startedAt * 1000).toLong()).atZone(clock.zone).format(DAY)}"

  private fun fail(
    directory: Path,
    failure: Throwable,
  ) {
    log.error("Radio episode for $directory failed", failure)
    Files.deleteIfExists(directory.resolve(TEMPORARY))
    save(directory.resolve(FAILED), EpisodeFailure(failure.message ?: failure.javaClass.simpleName, now()))
  }

  private fun now(): String = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString()

  companion object {
    private const val EPISODE = "episode.m4a"
    private const val TEMPORARY = "episode.tmp.m4a"
    private const val REVIEWED = "episode.review.m4a"
    private const val FAILED = "episode-failed.json"
    private const val OUTLINE = "outline.json"
    private const val EDIT_LIST = "edit-list.json"
    private val READY = setOf(RecordingState.CAPTURED, RecordingState.PARTIAL)
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
  }
}
