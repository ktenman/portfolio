package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.exception.RecordingInProgressException
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

@Service
class RadioArchiveService(
  private val radioReviewService: RadioReviewService,
  private val radioStorageService: RadioStorageService,
  private val radioProgressService: RadioProgressService,
  private val radioProperties: RadioProperties,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun recordings(): List<RecordingSummary> = directories(radioProperties.recordings).mapNotNull { readable(it, ::summary) }

  fun statuses(): List<RecordingStatus> = directories(radioProperties.recordings).mapNotNull { readable(it, ::readStatus) }

  fun transcript(name: String): List<TranscriptLine>? {
    val directory = directory(name, TRANSCRIPT_FILE) ?: return null
    val edit = read<EditList>(directory.resolve(EDIT_LIST))
    val music = edit?.music.orEmpty() + edit?.songs.orEmpty()
    return mark(music(lines(readTranscript(directory)), music), cutsOf(directory))
  }

  fun sections(name: String): List<TranscriptSection>? {
    val lines = transcript(name) ?: return null
    val directory = directory(name, TRANSCRIPT_FILE) ?: return null
    val status = readStatus(directory)
    val edit = read<EditList>(directory.resolve(EDIT_LIST))
    val decided = decided(directory, edit)
    val cuts = cuts(decided, status.seconds)
    return sections(timelineOf(cuts, decided, status), cuts, audibleOf(directory, status, edit), lines, status.title())
  }

  fun contexts(name: String): List<CutContext>? {
    val directory = directory(name, TRANSCRIPT_FILE) ?: return null
    val cuts = cutsOf(directory)
    if (cuts.isEmpty()) return emptyList()
    val lines = lines(readTranscript(directory))
    return cuts.map { context(it, lines) }
  }

  fun file(
    name: String,
    file: String,
  ): Resource? {
    if (!AUDIO.matches(file)) return null
    val directory = directory(name, STATUS_FILE) ?: return null
    val path = inside(directory, file) ?: return null
    if (Files.isReadable(path)) return FileSystemResource(path)
    return radioStorageService.resource(directory, file)
  }

  fun downloadName(
    name: String,
    file: String,
  ): String? = directory(name, STATUS_FILE)?.let { downloadName(name, readStatus(it), file, clock.zone) }

  fun review(
    name: String,
    review: CutReview,
  ): Boolean {
    val directory = directory(name, STATUS_FILE) ?: return false
    val edit = read<EditList>(directory.resolve(EDIT_LIST)) ?: return false
    val total = readStatus(directory).seconds
    return radioReviewService.review(directory, keep(cuts(edit, total), review.removed, total))
  }

  fun jingle(
    name: String,
    index: Int,
    review: JingleReview,
  ): Boolean {
    val directory = directory(name, EDIT_LIST) ?: return false
    val status = readStatus(directory)
    val match = cuts(read(directory.resolve(EDIT_LIST)), status.seconds).getOrNull(index)?.match ?: return false
    val path = stored(directory, index, match)
    when {
      !review.saved -> Files.deleteIfExists(path)
      Files.notExists(path) -> store(path, template(Timeline(directory, status.parts), match.start))
    }
    return true
  }

  fun add(
    name: String,
    cut: ManualCut,
  ): Boolean {
    val directory = directory(name, EDIT_LIST) ?: return false
    val edit = read<EditList>(directory.resolve(EDIT_LIST)) ?: return false
    val status = readStatus(directory)
    val db = loudness(Timeline(directory, status.parts))
    val snapped = ManualCut(rounded(snap(db, cut.start)), rounded(snap(db, cut.end)))
    require(snapped.start >= 0 && snapped.start < snapped.end && snapped.end <= status.seconds) {
      "Manual cut $snapped of $name is not a span within 0-${status.seconds} s"
    }
    require(cuts(edit, status.seconds).none { snapped.start < it.end && it.start < snapped.end }) {
      "Manual cut $snapped overlaps a cut of $name"
    }
    save(directory.resolve(EDIT_LIST), edit.copy(manual = edit.manual + snapped))
    return true
  }

  fun drop(
    name: String,
    index: Int,
  ): Boolean {
    val directory = directory(name, EDIT_LIST) ?: return false
    val edit = read<EditList>(directory.resolve(EDIT_LIST)) ?: return false
    val cut = cuts(edit, readStatus(directory).seconds).getOrNull(index)?.takeIf { it.kind == CutKind.MANUAL } ?: return false
    require(!cut.removed) { "Manual cut $index of $name is in the episode; keep it before dropping it" }
    save(directory.resolve(EDIT_LIST), edit.copy(manual = edit.manual.filterNot { it.start == cut.start && it.end == cut.end }))
    return true
  }

  fun find(
    name: String,
    choice: CutChoice,
  ): Boolean {
    val directory = directory(name, STATUS_FILE) ?: return false
    require(choice.programme in readStatus(directory).programmes) { "Recording $name holds no programme ${choice.programme}" }
    Files.deleteIfExists(directory.resolve(FAILED))
    return true
  }

  @Synchronized
  @OptIn(ExperimentalPathApi::class)
  fun delete(name: String): Boolean {
    val directory = directory(name, STATUS_FILE) ?: return false
    if (readStatus(directory).state == RecordingState.RECORDING) throw RecordingInProgressException(name)
    radioStorageService.delete(directory)
    val deleted = radioProperties.recordings.resolve(DELETED_FILE)
    save(deleted, read<List<String>>(deleted).orEmpty() + name)
    directory.deleteRecursively()
    return true
  }

  private fun <T> readable(
    directory: Path,
    read: (Path) -> T,
  ): T? = runCatching { read(directory) }.onFailure { log.warn("Cannot read radio recording $directory", it) }.getOrNull()

  private fun directory(
    name: String,
    required: String,
  ): Path? =
    directories(radioProperties.recordings)
      .firstOrNull { it.fileName.toString() == name }
      ?.takeIf { Files.exists(it.resolve(required)) }

  private fun inside(
    parent: Path,
    child: String,
  ): Path? {
    val path = parent.resolve(child).normalize()
    if (!path.startsWith(parent)) return null
    return path
  }

  private fun summary(directory: Path): RecordingSummary {
    val status = readStatus(directory)
    val edit = read<EditList>(directory.resolve(EDIT_LIST))
    val decided = decided(directory, edit)
    val cuts = cutsOf(directory, decided, status.seconds)
    val failure = read<EpisodeFailure>(directory.resolve(FAILED))
    return RecordingSummary(
      name = directory.fileName.toString(),
      programme = status.title(),
      status = status,
      sizeBytes = sizeOf(directory, status),
      transcribed = Files.exists(directory.resolve(TRANSCRIPT_FILE)),
      speech = readModel(directory.resolve(TRANSCRIPT_FILE)),
      sounds = readModel(directory.resolve(SOUNDS_FILE)),
      editList = edit,
      failure = failure,
      episodeBytes = size(directory, EPISODE),
      cuts = cuts,
      timeline = timelineOf(cuts, decided, status),
      audible = audibleOf(directory, status, edit),
      format = formatOf(directory, status),
      programmeCuts = radioProgressService.programmeCuts(directory, status, edit, failure != null),
    ).rendering()
  }

  private fun RecordingSummary.rendering(): RecordingSummary =
    copy(render = radioReviewService.progress(name), renderFailure = radioReviewService.failure(name))

  private fun sizeOf(
    directory: Path,
    status: RecordingStatus,
  ): Long = status.parts.sumOf { size(directory, it.file) ?: 0 }

  private fun decided(
    directory: Path,
    edit: EditList?,
  ): EditList? = radioReviewService.pending(directory)?.let { edit?.copy(keep = it) } ?: edit

  private fun formatOf(
    directory: Path,
    status: RecordingStatus,
  ): AudioFormat? =
    status.parts
      .firstOrNull()
      ?.let { read<Probe>(directory.resolve(it.file.removeSuffix(".mp3") + ".probe.json")) }
      ?.streams
      ?.firstOrNull()

  private fun timelineOf(
    cuts: List<Cut>,
    edit: EditList?,
    status: RecordingStatus,
  ): List<TimelineEntry> = timeline(cuts, edit?.topics.orEmpty(), status.seconds)

  private fun audibleOf(
    directory: Path,
    status: RecordingStatus,
    edit: EditList?,
  ): List<KeptSpan> {
    val keep = edit?.keep ?: return emptyList()
    return audible(coverage(directory, status.parts), keep.map { it.start to it.end }).map { KeptSpan(it.first, it.second) }
  }

  private fun cutsOf(directory: Path): List<Cut> = cuts(read(directory.resolve(EDIT_LIST)), readStatus(directory).seconds)

  private fun cutsOf(
    directory: Path,
    edit: EditList?,
    total: Double,
  ): List<Cut> =
    cuts(edit, total).mapIndexed { index, cut ->
      cut.copy(jingle = cut.match?.let { CutJingle(it.r, Files.exists(stored(directory, index, it))) })
    }

  private fun stored(
    directory: Path,
    index: Int,
    match: JingleMatch,
  ): Path = pcmOf(radioProperties.jingles, match.saved ?: "${directory.fileName}-$index")

  private fun size(
    directory: Path,
    file: String,
  ): Long? =
    directory.resolve(file).takeIf(Files::exists)?.let(Files::size) ?: read<Upload>(directory.resolve(UPLOAD))?.files?.get(file)?.size

  companion object {
    private const val EPISODE = "episode.m4a"
    private const val EDIT_LIST = "edit-list.json"
    private const val FAILED = "episode-failed.json"
  }
}
