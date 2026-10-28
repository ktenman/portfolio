package ee.tenman.portfolio.service.radio

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

@Service
class RadioSearchService(
  private val radioProperties: RadioProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val index = HashMap<Path, Pair<List<FileTime?>, IndexedRecording?>>()

  fun search(
    query: String,
    after: String?,
  ): RadioSearch = search(recordings(), query, after)

  @EventListener(ApplicationReadyEvent::class)
  fun warm() {
    runCatching { recordings() }.onFailure { log.warn("Cannot warm the radio search index", it) }
  }

  @Synchronized
  private fun recordings(): List<IndexedRecording> {
    val found = directories(radioProperties.recordings).filter { Files.exists(it.resolve(TRANSCRIPT_FILE)) }
    index.keys.retainAll(found.toSet())
    return found.mapNotNull(::recording)
  }

  private fun recording(directory: Path): IndexedRecording? {
    val stamp = FILES.map { file -> runCatching { Files.getLastModifiedTime(directory.resolve(file)) }.getOrNull() }
    val known = index[directory]
    if (known?.first == stamp) return known.second
    val loaded = runCatching { load(directory) }.onFailure { log.warn("Cannot index radio recording $directory", it) }
    if (loaded.exceptionOrNull() !is IOException) index[directory] = stamp to loaded.getOrNull()
    return loaded.getOrNull()
  }

  private fun load(directory: Path): IndexedRecording {
    val edit = read<EditList>(directory.resolve(EDIT_LIST_FILE)).decided(read(directory.resolve(REVIEW_FILE)))
    return indexed(directory.fileName.toString(), readStatus(directory), readTranscript(directory), edit)
  }

  companion object {
    private val FILES = listOf(STATUS_FILE, TRANSCRIPT_FILE, EDIT_LIST_FILE, REVIEW_FILE)
  }
}
