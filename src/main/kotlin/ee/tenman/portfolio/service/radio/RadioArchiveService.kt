package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.configuration.JsonMapperFactory
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
  fun recordings(): List<RecordingSummary> {
    val root = radioProperties.recordings
    if (!Files.isDirectory(root)) return emptyList()
    return Files
      .list(root)
      .use { it.toList() }
      .filter { Files.exists(it.resolve(STATUS_FILE)) }
      .sortedDescending()
      .map { summary(it) }
  }

  fun transcript(name: String): List<TranscriptLine>? = directory(name, TRANSCRIPT_FILE)?.let { lines(readTranscript(it)) }

  fun file(
    name: String,
    file: String,
  ): Resource? {
    if (!FILE.matches(file)) return null
    return directory(name, STATUS_FILE)?.let { FileSystemResource(it.resolve(file)) }?.takeIf { it.isReadable }
  }

  fun review(
    name: String,
    keep: List<KeptSpan>,
  ): EditList? = directory(name, STATUS_FILE)?.let { radioEpisodeService.review(it, keep) }

  fun check(name: String): Boolean {
    val directory = directory(name, TRANSCRIPT_FILE) ?: return false
    val check = TranscriptCheck(Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString())
    Files.writeString(directory.resolve(CHECKED), JsonMapperFactory.instance.writeValueAsString(check))
    return true
  }

  private fun directory(
    name: String,
    required: String,
  ): Path? = radioProperties.recordings.resolve(name).takeIf { NAME.matches(name) && Files.exists(it.resolve(required)) }

  private fun summary(directory: Path): RecordingSummary {
    val status = readStatus(directory)
    return RecordingSummary(
      name = directory.fileName.toString(),
      status = status,
      sizeBytes = status.parts.sumOf { size(directory.resolve(it.file)) ?: 0 },
      transcribed = Files.exists(directory.resolve(TRANSCRIPT_FILE)),
      transcriptCheckedAt = read<TranscriptCheck>(directory.resolve(CHECKED))?.checkedAt,
      editList = read(directory.resolve(EDIT_LIST)),
      failure = read(directory.resolve(FAILED)),
      episodeBytes = size(directory.resolve(EPISODE)),
    )
  }

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
