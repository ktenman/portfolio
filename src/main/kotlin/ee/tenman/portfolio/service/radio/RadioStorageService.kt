package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.RadioProgramme
import ee.tenman.portfolio.repository.RadioProgrammeRepository
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

@Service
class RadioStorageService(
  private val minioClient: MinioClient,
  private val radioEpisodeService: RadioEpisodeService,
  private val radioProperties: RadioProperties,
  private val clock: Clock,
  private val radioProgrammeRepository: RadioProgrammeRepository,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val lock = ReentrantReadWriteLock()
  private val failures = ConcurrentHashMap<Path, Instant>()

  @Synchronized
  fun store() {
    prepare()
    val programmes = radioProgrammeRepository.findAll()
    directories(radioProperties.recordings).forEach { directory ->
      runCatching { store(directory, programmes) }.onFailure { log.error("Cannot store radio recording $directory", it) }
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

  @Synchronized
  fun drop() =
    claim {
      val failed = due().filterNot(::dropped)
      check(failed.isEmpty()) { "Cannot drop the raw audio of radio recordings ${failed.map { it.fileName }}" }
    }

  fun <T> hold(block: () -> T): T = lock.read(block)

  fun due(): List<Path> {
    if (!radioProperties.dropRaw) return emptyList()
    val programmes = radioProgrammeRepository.findAll()
    return directories(radioProperties.recordings).filter { !paused(it) && due(it, programmes) }
  }

  private fun prepare() {
    val bucket = radioProperties.bucket
    if (minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) return
    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
  }

  private fun claim(block: () -> Unit) {
    val writer = lock.writeLock()
    if (!writer.tryLock()) return
    try {
      block()
    } finally {
      writer.unlock()
    }
  }

  private fun store(
    directory: Path,
    programmes: List<RadioProgramme>,
  ) {
    val status = readStatus(directory)
    if (status.state == RecordingState.RECORDING) return
    sync(directory, !radioProperties.dropRaw || keeps(status, programmes) || old(status))
    claim {
      if (processed(directory)) pcm(directory).forEach(Files::delete)
      if (old(status)) offload(directory)
    }
  }

  private fun sync(
    directory: Path,
    withRaw: Boolean,
  ) {
    val saved = saved(directory)
    val local = local(directory).filterKeys { withRaw || !raw(it) }
    val changed = local.filter { saved[it.key] != it.value }.keys
    val gone = (saved.keys - local.keys).filterNot(AUDIO::matches)
    if (changed.isEmpty() && gone.isEmpty()) return
    changed.forEach { upload(directory, it) }
    gone.forEach { remove(directory, it) }
    save(directory.resolve(UPLOAD), Upload(radioProperties.bucket, saved - gone.toSet() + local))
  }

  private fun offload(directory: Path) {
    val saved = saved(directory)
    val audio = files(directory).filter { AUDIO.matches(it.fileName.toString()) && saved[it.fileName.toString()] == stored(it) }
    release(directory, saved, audio)
  }

  private fun release(
    directory: Path,
    saved: Map<String, StoredFile>,
    audio: List<Path>,
  ) {
    if (EPISODE_FILE in saved) forget(directory, saved)
    (audio + pcm(directory)).forEach(Files::delete)
    Files.deleteIfExists(directory.resolve(FINGERPRINTS))
  }

  private fun forget(
    directory: Path,
    saved: Map<String, StoredFile>,
  ) {
    val parts = saved.keys.filter(::raw)
    if (parts.isEmpty()) return
    parts.forEach { remove(directory, it) }
    save(directory.resolve(UPLOAD), Upload(radioProperties.bucket, saved - parts.toSet()))
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

  private fun raw(file: String): Boolean = AUDIO.matches(file) && file != EPISODE_FILE

  private fun saved(directory: Path): Map<String, StoredFile> = read<Upload>(directory.resolve(UPLOAD))?.files.orEmpty()

  private fun parts(directory: Path): List<Path> = files(directory).filter { raw(it.fileName.toString()) }

  private fun pcm(directory: Path): List<Path> = files(directory).filter { it.fileName.toString().endsWith(".pcm") }

  private fun files(directory: Path): List<Path> = Files.list(directory).use { it.toList() }

  private fun key(
    directory: Path,
    file: String,
  ): String = "${directory.fileName}/$file"

  private fun processed(directory: Path): Boolean =
    Files.exists(directory.resolve(TRANSCRIPT_FILE)) && radioEpisodeService.todo(directory).isEmpty()

  private fun old(status: RecordingStatus): Boolean =
    ended(status).isBefore(Instant.now(clock).minus(radioProperties.localDays, ChronoUnit.DAYS))

  private fun ended(status: RecordingStatus): Instant = Instant.ofEpochMilli(((status.startedAt + status.seconds) * 1000).toLong())

  private fun due(
    directory: Path,
    programmes: List<RadioProgramme>,
  ): Boolean =
    runCatching { ripe(directory, programmes) }
      .onFailure {
        failures[directory] = Instant.now(clock)
        log.warn("Cannot tell whether radio recording $directory holds raw audio to drop", it)
      }.getOrDefault(false)

  private fun ripe(
    directory: Path,
    programmes: List<RadioProgramme>,
  ): Boolean {
    if (parts(directory).isEmpty() && saved(directory).keys.none(::raw)) return false
    val status = readStatus(directory)
    val cutoff = Instant.now(clock).minus(RAW_HOURS, ChronoUnit.HOURS)
    return status.state != RecordingState.RECORDING &&
      !keeps(status, programmes) &&
      ended(status).isBefore(cutoff) &&
      EPISODE_FILE in saved(directory) &&
      Files.notExists(directory.resolve(REVIEW_FILE)) &&
      read<EditList>(directory.resolve(EDIT_LIST_FILE)).found().containsAll(status.programmes)
  }

  private fun keeps(
    status: RecordingStatus,
    programmes: List<RadioProgramme>,
  ): Boolean = status.uncut(programmes) || status.unlisted(programmes)

  private fun paused(directory: Path): Boolean =
    failures[directory]?.isAfter(Instant.now(clock).minus(PAUSE_HOURS, ChronoUnit.HOURS)) ?: false

  private fun dropped(directory: Path): Boolean =
    runCatching { release(directory, saved(directory), parts(directory)) }
      .onSuccess { log.info("Dropped the raw audio of radio recording ${directory.fileName}") }
      .onFailure {
        failures[directory] = Instant.now(clock)
        log.error("Cannot drop the raw audio of radio recording $directory", it)
      }.isSuccess

  companion object {
    private const val RAW_HOURS = 24L
    private const val PAUSE_HOURS = 1L
  }
}
