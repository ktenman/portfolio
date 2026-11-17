package ee.tenman.portfolio.service.radio

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path

@Service
class RadioDigestArchiveService(
  private val radioProperties: RadioProperties,
) {
  fun digests(): List<RadioDigest> = DigestShow.entries.flatMap { show -> digests(root(show)).mapNotNull { digest(show, it) } }

  fun recording(name: String): RecordingSummary? = folder(name)?.let { (show, path) -> digest(show, path) }?.recording

  fun sections(name: String): List<TranscriptSection>? =
    folder(name)?.let { (_, path) -> read<List<TranscriptSection>>(path.resolve(TRANSCRIPT_FILE)) }

  fun file(
    name: String,
    file: String,
  ): Resource? {
    if (file != EPISODE_FILE) return null
    return folder(name)
      ?.second
      ?.resolve(EPISODE_FILE)
      ?.takeIf(Files::isReadable)
      ?.let(::FileSystemResource)
  }

  private fun folder(name: String): Pair<DigestShow, Path>? {
    val show = DigestShow.entries.firstOrNull { name.startsWith(it.prefix) } ?: return null
    return digests(root(show)).firstOrNull { it.fileName.toString() == name.removePrefix(show.prefix) }?.let { show to it }
  }

  private fun digest(
    show: DigestShow,
    folder: Path,
  ): RadioDigest? {
    val digest = read<Digest>(folder.resolve(DIGEST_FILE))?.takeIf { it.stories.isNotEmpty() } ?: return null
    return RadioDigest(show, digestSummary(show, digest, Files.size(folder.resolve(EPISODE_FILE))), digest.stories)
  }

  private fun root(show: DigestShow): Path = radioProperties.recordings.resolve(show.directory)
}
