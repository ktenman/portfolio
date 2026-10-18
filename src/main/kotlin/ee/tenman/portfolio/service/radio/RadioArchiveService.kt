package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.configuration.JsonMapperFactory
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import tools.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class RadioArchiveService(
  private val radioEpisodeService: RadioEpisodeService,
  private val radioProperties: RadioProperties,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun recordings(): List<RecordingSummary> {
    val root = radioProperties.recordings
    if (!Files.isDirectory(root)) return emptyList()
    return Files
      .list(root)
      .use { it.toList() }
      .filter { NAME.matches(it.fileName.toString()) && Files.exists(it.resolve(STATUS_FILE)) }
      .sortedDescending()
      .mapNotNull { directory ->
        runCatching { summary(directory) }.onFailure { log.warn("Cannot read radio recording $directory", it) }.getOrNull()
      }
  }

  fun transcript(name: String): List<TranscriptLine>? {
    val directory = directory(name, TRANSCRIPT_FILE) ?: return null
    return mark(lines(readTranscript(directory)), cutsOf(directory))
  }

  fun contexts(name: String): List<CutContext>? {
    val directory = directory(name, TRANSCRIPT_FILE) ?: return null
    val lines = lines(readTranscript(directory))
    return cutsOf(directory).map { context(it, lines) }
  }

  fun file(
    name: String,
    file: String,
  ): Resource? {
    if (!FILE.matches(file)) return null
    val path = directory(name, STATUS_FILE)?.let { inside(it, file) } ?: return null
    return FileSystemResource(path).takeIf { it.isReadable }
  }

  fun review(
    name: String,
    review: CutReview,
  ): EditList? {
    val directory = directory(name, STATUS_FILE) ?: return null
    val edit = read<EditList>(directory.resolve(EDIT_LIST)) ?: return null
    val total = readStatus(directory).seconds
    return radioEpisodeService.review(directory, keep(cuts(edit, total), review.removed, total))
  }

  fun check(name: String): Boolean {
    val directory = directory(name, TRANSCRIPT_FILE) ?: return false
    val check = TranscriptCheck(Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString())
    save(directory.resolve(CHECKED), check)
    return true
  }

  private fun directory(
    name: String,
    required: String,
  ): Path? {
    if (!NAME.matches(name)) return null
    val directory = inside(radioProperties.recordings.normalize(), name) ?: return null
    if (!Files.exists(directory.resolve(required))) return null
    return directory
  }

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
    return RecordingSummary(
      name = directory.fileName.toString(),
      programme = PROGRAMME,
      status = status,
      sizeBytes = status.parts.sumOf { size(directory.resolve(it.file)) ?: 0 },
      transcribed = Files.exists(directory.resolve(TRANSCRIPT_FILE)),
      transcriptCheckedAt = read<TranscriptCheck>(directory.resolve(CHECKED))?.checkedAt,
      editList = edit,
      failure = read(directory.resolve(FAILED)),
      episodeBytes = size(directory.resolve(EPISODE)),
      cuts = cuts(edit, status.seconds),
      timeline = timeline(edit, status.seconds),
    )
  }

  private fun cutsOf(directory: Path): List<Cut> = cuts(read(directory.resolve(EDIT_LIST)), readStatus(directory).seconds)

  private fun size(path: Path): Long? = path.takeIf(Files::exists)?.let(Files::size)

  private inline fun <reified T> read(path: Path): T? =
    path.takeIf(Files::exists)?.let { JsonMapperFactory.instance.readValue<T>(Files.readString(it)) }

  companion object {
    private const val EPISODE = "episode.m4a"
    private const val EDIT_LIST = "edit-list.json"
    private const val FAILED = "episode-failed.json"
    private const val CHECKED = "transcript-checked.json"
    private val NAME = Regex("[0-9a-z][0-9a-z-]*")
    private val FILE = Regex("episode\\.m4a|raw(-part-\\d{3})?\\.mp3|transcript\\.(srt|txt)")
  }
}
