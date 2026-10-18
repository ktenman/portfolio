package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import io.minio.GetObjectArgs
import io.minio.ListObjectsArgs
import io.minio.MinioClient
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Clock
import java.time.Instant

@IntegrationTest
class RadioStorageServiceIT {
  @Resource
  private lateinit var radioStorageService: RadioStorageService

  @Resource
  private lateinit var radioProperties: RadioProperties

  @Resource
  private lateinit var minioClient: MinioClient

  @Resource
  private lateinit var clock: Clock

  @BeforeEach
  fun setup() {
    radioProperties.recordings.toFile().deleteRecursively()
  }

  private fun recording(
    name: String,
    startedAt: Double = Instant.now(clock).epochSecond.toDouble(),
    state: RecordingState = RecordingState.CAPTURED,
    programmes: List<String> = listOf("Tarkade klubi"),
    continuous: Boolean = false,
  ): Path {
    val directory = Files.createDirectories(radioProperties.recordings.resolve(name))
    val parts = listOf(RecordingPart("raw.mp3", 0.0))
    save(directory.resolve(STATUS_FILE), RecordingStatus(state, startedAt, 60.0, parts, programmes, continuous = continuous))
    Files.writeString(directory.resolve("raw.mp3"), "Kuku õhtu")
    Files.writeString(directory.resolve("raw.pcm"), "pcm")
    Files.writeString(directory.resolve(TRANSCRIPT_FILE), "{}")
    return directory
  }

  private fun keys(name: String): List<String> {
    val args =
      ListObjectsArgs
        .builder()
        .bucket(radioProperties.bucket)
        .prefix("$name/")
        .build()
    return minioClient.listObjects(args).map { it.get().objectName() }.sorted()
  }

  private fun text(key: String): String {
    val args =
      GetObjectArgs
        .builder()
        .bucket(radioProperties.bucket)
        .`object`(key)
        .build()
    return minioClient.getObject(args).use { String(it.readAllBytes()) }
  }

  @Test
  fun `should upload the audio and the json of a finished recording but not its pcm`() {
    recording("2026-10-01-1200-tarkade-klubi")
    radioStorageService.store()
    expect(keys("2026-10-01-1200-tarkade-klubi")).toEqual(
      listOf(
        "2026-10-01-1200-tarkade-klubi/raw.mp3",
        "2026-10-01-1200-tarkade-klubi/status.json",
        "2026-10-01-1200-tarkade-klubi/transcript.json",
      ),
    )
  }

  @Test
  fun `should leave a recording still on air on the disk only`() {
    recording("2026-10-02-1200-tarkade-klubi", state = RecordingState.RECORDING)
    radioStorageService.store()
    expect(keys("2026-10-02-1200-tarkade-klubi")).toEqual(emptyList())
  }

  @Test
  fun `should delete the pcm once no cuts are pending`() {
    val directory = recording("2026-10-03-1200-tarkade-klubi")
    radioStorageService.store()
    expect(Files.exists(directory.resolve("raw.pcm"))).toEqual(false)
  }

  @Test
  fun `should keep the pcm while the cuts of the recording are pending`() {
    val directory = recording("2026-10-04-1200-keskpaevatund", programmes = listOf(PROGRAMME))
    radioStorageService.store()
    expect(Files.exists(directory.resolve("raw.pcm"))).toEqual(true)
  }

  @Test
  fun `should delete the local audio of a recording older than the kept days`() {
    val directory = recording("2026-09-01-1200-tarkade-klubi", startedAt = 1788253200.0)
    radioStorageService.store()
    expect(listOf("raw.mp3", STATUS_FILE).map { Files.exists(directory.resolve(it)) }).toEqual(listOf(false, true))
  }

  @Test
  fun `should keep the local audio of a recent recording`() {
    val directory = recording("2026-10-05-1200-tarkade-klubi")
    radioStorageService.store()
    expect(Files.exists(directory.resolve("raw.mp3"))).toEqual(true)
  }

  @Test
  fun `should upload an edit list again after it changes`() {
    val directory = recording("2026-10-06-1200-tarkade-klubi")
    Files.writeString(directory.resolve("edit-list.json"), "{\"reviewed\":false}")
    radioStorageService.store()
    Files.writeString(directory.resolve("edit-list.json"), "{\"reviewed\":true}")
    Files.setLastModifiedTime(directory.resolve("edit-list.json"), FileTime.from(Instant.now(clock).plusSeconds(60)))
    radioStorageService.store()
    expect(text("2026-10-06-1200-tarkade-klubi/edit-list.json")).toEqual("{\"reviewed\":true}")
  }

  @Test
  fun `should remove a file from the bucket once it is deleted from the disk`() {
    val directory = recording("2026-10-07-1200-tarkade-klubi")
    Files.writeString(directory.resolve("episode-failed.json"), "{}")
    radioStorageService.store()
    Files.delete(directory.resolve("episode-failed.json"))
    radioStorageService.store()
    expect(keys("2026-10-07-1200-tarkade-klubi").map { it.substringAfter('/') }).toEqual(listOf("raw.mp3", STATUS_FILE, TRANSCRIPT_FILE))
  }

  @Test
  fun `should keep a continuous recording out of the bucket`() {
    recording("2026-10-08-0000-pidev", continuous = true)
    radioStorageService.store()
    expect(keys("2026-10-08-0000-pidev")).toEqual(emptyList())
  }

  @Test
  fun `should delete a continuous recording a week after it ends`() {
    val directory = recording("2026-09-02-0000-pidev", startedAt = 1788339600.0, continuous = true)
    radioStorageService.store()
    expect(Files.exists(directory)).toEqual(false)
  }

  @Test
  fun `should keep a continuous recording within its week`() {
    val directory = recording("2026-10-09-0000-pidev", continuous = true)
    radioStorageService.store()
    expect(Files.exists(directory.resolve("raw.mp3"))).toEqual(true)
  }
}
