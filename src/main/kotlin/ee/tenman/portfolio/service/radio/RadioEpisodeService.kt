package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.configuration.JsonMapperFactory
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
  private val radioProperties: RadioProperties,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun make(directory: Path) {
    if (!pending(directory)) return
    runCatching { render(directory) }.onFailure { fail(directory, it) }
  }

  private fun pending(directory: Path): Boolean {
    val ready =
      listOf(EPISODE, FAILED).none { Files.exists(directory.resolve(it)) } &&
        listOf(STATUS_FILE, TRANSCRIPT_FILE).all { Files.exists(directory.resolve(it)) } &&
        readStatus(directory).let { it.state in READY && it.programmes == listOf(PROGRAMME) }
    if (!ready || Files.isWritable(directory)) return ready
    log.warn("Cannot write the radio episode into $directory")
    return false
  }

  private fun render(directory: Path): Path {
    val status = readStatus(directory)
    val lines = lines(readTranscript(directory))
    val timeline = Timeline(directory, status.parts)
    val db = loudness(timeline)
    val (reads, model) = reads(directory) { describe(status, lines, events(db)) }
    val matched = reads.filterNotNull().mapNotNull { match(it, lines) }
    check(matched.isNotEmpty()) { "No outline read in ${directory.resolve(OUTLINE)} matches the transcript" }
    val agreed = consensus(matched)
    val placed = radioCutService.boundaries(timeline, db, agreed, lines, library(radioProperties.jingles))
    val keep = placed.map { it.seconds }.chunked(2).map { it[0] to it[1] }
    val kept = audible(timeline.spans, keep)
    val marks = chapters(kept, agreed.chapters)
    val temporary = directory.resolve(TEMPORARY)
    radioRenderService.render(timeline, keep, marks, title(status), temporary)
    val summary = OutlineSummary(model, reads.size, matched.size, agreed.voters)
    save(directory.resolve(EDIT_LIST), editList(status, placed, keep, kept, marks, agreed, summary))
    return Files.move(temporary, directory.resolve(EPISODE), StandardCopyOption.ATOMIC_MOVE)
  }

  private fun reads(
    directory: Path,
    text: () -> String,
  ): Pair<List<OutlineRead?>, String?> {
    val saved = directory.resolve(OUTLINE)
    if (Files.exists(saved)) return JsonMapperFactory.instance.readValue<List<OutlineRead?>>(Files.readString(saved)) to null
    val model = radioProperties.outlineModel
    val reads = radioOutlineService.reads(text(), model)
    check(reads.any { it != null }) { "No outline read came back from ${model.modelId} for $directory" }
    save(saved, reads)
    return reads to model.modelId
  }

  private fun editList(
    status: RecordingStatus,
    placed: List<Boundary>,
    keep: List<Pair<Double, Double>>,
    kept: List<Pair<Double, Double>>,
    marks: List<Pair<Double, String>>,
    agreed: Consensus,
    summary: OutlineSummary,
  ) = EditList(
    keep = keep.map { KeptSpan(it.first, it.second) },
    durationSeconds = rounded(kept.sumOf { it.second - it.first }),
    parts = status.parts,
    boundaries = placed,
    hosts = agreed.hosts,
    outline = summary,
    chapters = marks.map { EpisodeChapter(rounded(it.first), it.second) },
    reviewed = false,
    renderedAt = now(),
    topics = agreed.chapters.map { EpisodeChapter(rounded(it.time), it.title) },
    music = listOfNotNull(KeptSpan(placed.first().seconds, agreed.firstSpeechTime).takeIf { agreed.opensWithMusic }),
  )

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
    val spans = keep.map { it.start to min(it.end, timeline.seconds) }
    require(ordered(spans) && spans.all { audible(timeline.spans, listOf(it)).isNotEmpty() }) {
      "Keep list $keep is not ordered audible spans within 0-${timeline.seconds} s"
    }
    val kept = audible(timeline.spans, spans)
    val marks = chapters(kept, edit.topics.map { OutlineChapter(it.start, it.title) })
    val temporary = directory.resolve(REVIEWED)
    runCatching { radioRenderService.render(timeline, spans, marks, title(status), temporary) }
      .onFailure { Files.deleteIfExists(temporary) }
      .getOrThrow()
    Files.move(temporary, directory.resolve(EPISODE), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    return reviewed(edit, spans.map { KeptSpan(it.first, it.second) }, kept, marks)
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

  private fun title(status: RecordingStatus): String =
    "$PROGRAMME ${Instant.ofEpochMilli((status.startedAt * 1000).toLong()).atZone(clock.zone).format(DAY)}"

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
