package ee.tenman.portfolio.service.radio

import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.RemoveObjectArgs
import io.minio.UploadObjectArgs
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class RadioStorageService(
  private val minioClient: MinioClient,
  private val radioEpisodeService: RadioEpisodeService,
  private val radioProperties: RadioProperties,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun store() {
    prepare()
    directories(radioProperties.recordings).forEach { directory ->
      runCatching { store(directory) }.onFailure { log.error("Cannot store radio recording $directory", it) }
    }
  }

  fun resource(
    directory: Path,
    file: String,
  ): Resource? {
    val upload = read<Upload>(directory.resolve(UPLOAD)) ?: return null
    val stored = upload.files[file] ?: return null
    return StoredResource(minioClient, upload.bucket, key(directory, file), stored)
  }

  fun delete(directory: Path) {
    val upload = read<Upload>(directory.resolve(UPLOAD)) ?: return
    (upload.files.keys + "").forEach { remove(directory, it) }
  }

  private fun prepare() {
    val bucket = radioProperties.bucket
    if (minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) return
    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
  }

  private fun store(directory: Path) {
    val status = readStatus(directory)
    if (status.state == RecordingState.RECORDING) return
    sync(directory)
    if (processed(directory)) pcm(directory).forEach(Files::delete)
    if (old(status)) offload(directory)
  }

  private fun sync(directory: Path) {
    val saved = read<Upload>(directory.resolve(UPLOAD))?.files.orEmpty()
    val local = local(directory)
    val changed = local.filter { saved[it.key] != it.value }.keys
    val gone = (saved.keys - local.keys).filterNot(AUDIO::matches)
    if (changed.isEmpty() && gone.isEmpty()) return
    changed.forEach { upload(directory, it) }
    gone.forEach { remove(directory, it) }
    save(directory.resolve(UPLOAD), Upload(radioProperties.bucket, saved - gone.toSet() + local))
  }

  private fun offload(directory: Path) {
    val saved = read<Upload>(directory.resolve(UPLOAD))?.files.orEmpty()
    val audio = files(directory).filter { AUDIO.matches(it.fileName.toString()) && saved[it.fileName.toString()] == stored(it) }
    (audio + pcm(directory)).forEach(Files::delete)
  }

  private fun upload(
    directory: Path,
    file: String,
  ) {
    val path = directory.resolve(file).toString()
    minioClient.uploadObject(
      UploadObjectArgs
        .builder()
        .bucket(radioProperties.bucket)
        .`object`(key(directory, file))
        .filename(path)
        .build(),
    )
  }

  private fun remove(
    directory: Path,
    file: String,
  ) {
    minioClient.removeObject(
      RemoveObjectArgs
        .builder()
        .bucket(radioProperties.bucket)
        .`object`(key(directory, file))
        .build(),
    )
  }

  private fun local(directory: Path): Map<String, StoredFile> =
    files(directory).filter { kept(it.fileName.toString()) }.associate { it.fileName.toString() to stored(it) }

  private fun kept(file: String): Boolean = AUDIO.matches(file) || file.endsWith(".json") && file != UPLOAD

  private fun pcm(directory: Path): List<Path> = files(directory).filter { it.fileName.toString().endsWith(".pcm") }

  private fun files(directory: Path): List<Path> = Files.list(directory).use { it.toList() }

  private fun key(
    directory: Path,
    file: String,
  ): String = "${directory.fileName}/$file"

  private fun processed(directory: Path): Boolean =
    Files.exists(directory.resolve(TRANSCRIPT_FILE)) && radioEpisodeService.todo(directory).isEmpty()

  private fun old(status: RecordingStatus): Boolean {
    val ended = Instant.ofEpochMilli(((status.startedAt + status.seconds) * 1000).toLong())
    return ended.isBefore(Instant.now(clock).minus(radioProperties.localDays, ChronoUnit.DAYS))
  }
}
