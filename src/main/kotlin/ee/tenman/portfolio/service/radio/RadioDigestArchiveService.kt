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
  fun digests(): List<RadioDigest> = digests(root()).mapNotNull(::digest)

  fun recording(name: String): RecordingSummary? = folder(name)?.let(::digest)?.recording

  fun sections(name: String): List<TranscriptSection>? = folder(name)?.let { read<List<TranscriptSection>>(it.resolve(TRANSCRIPT_FILE)) }

  fun file(
    name: String,
    file: String,
  ): Resource? {
    if (file != EPISODE_FILE) return null
    return folder(name)?.resolve(EPISODE_FILE)?.takeIf(Files::isReadable)?.let(::FileSystemResource)
  }

  private fun folder(name: String): Path? {
    if (!name.startsWith(DIGEST_PREFIX)) return null
    return digests(root()).firstOrNull { it.fileName.toString() == name.removePrefix(DIGEST_PREFIX) }
  }

  private fun digest(folder: Path): RadioDigest? {
    val digest = read<Digest>(folder.resolve(DIGEST_FILE))?.takeIf { it.stories.isNotEmpty() } ?: return null
    return RadioDigest(digestSummary(digest, Files.size(folder.resolve(EPISODE_FILE))), digest.stories)
  }

  private fun root(): Path = radioProperties.recordings.resolve(DIGEST_DIRECTORY)
}
