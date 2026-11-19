package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.notToContain
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.RadioProgramme
import ee.tenman.portfolio.repository.RadioProgrammeRepository
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
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

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

  @Resource
  private lateinit var radioEpisodeService: RadioEpisodeService

  @Resource
  private lateinit var radioProgrammeRepository: RadioProgrammeRepository

  @BeforeEach
  fun setup() {
    radioProperties.recordings.toFile().deleteRecursively()
    radioProgrammeRepository.save(RadioProgramme(1, "Tarkade klubi", "tarkade-klubi"))
    radioProgrammeRepository.save(RadioProgramme(2, "Keskpäevatund", "keskpaevatund"))
  }

  private var now = Instant.EPOCH

  private val moving =
    object : Clock() {
      override fun getZone(): ZoneId = ZoneOffset.UTC

      override fun withZone(zone: ZoneId): Clock = this

      override fun instant(): Instant = now
    }

  private fun storage(
    dropRaw: Boolean,
    bucket: String = radioProperties.bucket,
    clock: Clock = this.clock,
  ): RadioStorageService {
    val properties = radioProperties.copy(dropRaw = dropRaw, bucket = bucket)
    return RadioStorageService(minioClient, radioEpisodeService, properties, clock, radioProgrammeRepository)
  }

  private fun sweep(dropRaw: Boolean) = storage(dropRaw).apply { store() }.drop()

  private fun programme(change: RadioProgramme.() -> Unit) =
    radioProgrammeRepository.save(checkNotNull(radioProgrammeRepository.findByExternalId(1)).apply(change))

  private fun recording(
    name: String,
    startedAt: Double = Instant.now(clock).epochSecond.toDouble(),
    state: RecordingState = RecordingState.CAPTURED,
    programmes: List<String> = listOf("Tarkade klubi"),
  ): Path {
    val directory = Files.createDirectories(radioProperties.recordings.resolve(name))
    save(directory.resolve(STATUS_FILE), RecordingStatus(state, startedAt, 60.0, listOf(RecordingPart("raw.mp3", 0.0)), programmes))
    Files.writeString(directory.resolve("raw.mp3"), "Kuku õhtu")
    Files.writeString(directory.resolve("raw.pcm"), "pcm")
    Files.writeString(directory.resolve(TRANSCRIPT_FILE), "{}")
    return directory
  }

  private fun cut(
    name: String,
    hours: Long = 25,
    programmes: List<String> = listOf("Tarkade klubi"),
    found: List<String> = programmes,
  ): Path {
    val start = Instant.now(clock).minus(hours, ChronoUnit.HOURS).epochSecond
    val directory = recording(name, startedAt = start.toDouble(), programmes = programmes)
    save(directory.resolve(EDIT_LIST_FILE), editList(boundaries = found.map { Boundary("algus", 0.0, "jingle", false, programme = it) }))
    Files.writeString(directory.resolve(EPISODE_FILE), "puhas saade")
    writeFingerprints(directory, longArrayOf(42))
    return directory
  }

  private fun uploaded(name: String): Path = cut(name).also { storage(dropRaw = false).store() }

  private fun failing(clock: Clock = this.clock) = storage(dropRaw = true, bucket = "puudub", clock = clock)

  private fun keys(name: String): List<String> =
    minioClient
      .listObjects(
        ListObjectsArgs
        .builder()
        .bucket(radioProperties.bucket)
        .prefix("$name/")
        .build(),
          ).map { it.get().objectName() }
      .sorted()

  @Test
  fun `should upload the audio and the json of a finished recording but not its pcm`() {
    recording("2026-10-01-1200-tarkade-klubi")
    storage(dropRaw = false).store()
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
  fun `should delete the pcm once the cut search has failed`() {
    val directory = recording("2026-10-03-1200-tarkade-klubi")
    Files.writeString(directory.resolve("episode-failed.json"), "{}")
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
    storage(dropRaw = false).store()
    expect(listOf("raw.mp3", STATUS_FILE).map { Files.exists(directory.resolve(it)) }).toEqual(listOf(false, true))
  }

  @Test
  fun `should delete the fingerprints with the offloaded audio`() {
    val directory = recording("2026-09-01-1200-tarkade-klubi", startedAt = 1788253200.0)
    writeFingerprints(directory, longArrayOf(42))
    storage(dropRaw = false).store()
    expect(Files.exists(directory.resolve(FINGERPRINTS))).toEqual(false)
  }

  @Test
  fun `should keep only the episode audio in the bucket once a recording is older than the kept days`() {
    val directory = recording("2026-09-02-1200-tarkade-klubi", startedAt = 1788253200.0)
    Files.writeString(directory.resolve(EPISODE_FILE), "puhas saade")
    storage(dropRaw = false).store()
    expect(keys("2026-09-02-1200-tarkade-klubi").map { it.substringAfter('/') }).toEqual(listOf(EPISODE_FILE, STATUS_FILE, TRANSCRIPT_FILE))
  }

  @Test
  fun `should stop serving the raw audio of a recording whose episode alone is kept`() {
    val directory = recording("2026-09-03-1200-tarkade-klubi", startedAt = 1788253200.0)
    Files.writeString(directory.resolve(EPISODE_FILE), "puhas saade")
    storage(dropRaw = false).store()
    expect(radioStorageService.resource(directory, "raw.mp3")).toEqual(null)
  }

  @Test
  fun `should keep the raw audio of an old recording that has no episode`() {
    recording("2026-09-04-1200-tarkade-klubi", startedAt = 1788253200.0)
    storage(dropRaw = false).store()
    expect(keys("2026-09-04-1200-tarkade-klubi").map { it.substringAfter('/') }).toEqual(listOf("raw.mp3", STATUS_FILE, TRANSCRIPT_FILE))
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
    val key = "2026-10-06-1200-tarkade-klubi/edit-list.json"
    val stored =
      minioClient
        .getObject(
        GetObjectArgs
      .builder()
      .bucket(radioProperties.bucket)
      .`object`(key)
      .build(),
          ).use {
      String(it.readAllBytes())
    }
    expect(stored).toEqual("{\"reviewed\":true}")
  }

  @Test
  fun `should remove a file from the bucket once it is deleted from the disk`() {
    val directory = recording("2026-10-07-1200-tarkade-klubi")
    Files.writeString(directory.resolve("episode-failed.json"), "{}")
    storage(dropRaw = false).store()
    Files.delete(directory.resolve("episode-failed.json"))
    storage(dropRaw = false).store()
    expect(keys("2026-10-07-1200-tarkade-klubi").map { it.substringAfter('/') }).toEqual(listOf("raw.mp3", STATUS_FILE, TRANSCRIPT_FILE))
  }

  @Test
  fun `should remove the stored objects of a deleted recording`() {
    val directory = recording("2026-10-08-1200-tarkade-klubi")
    radioStorageService.store()
    radioStorageService.delete(directory)
    expect(keys("2026-10-08-1200-tarkade-klubi")).toEqual(emptyList())
  }

  @Test
  fun `should not upload the raw audio while raw audio is dropped`() {
    recording("2026-10-09-1200-tarkade-klubi")
    storage(dropRaw = true).store()
    expect(keys("2026-10-09-1200-tarkade-klubi").map { it.substringAfter('/') }).toEqual(listOf(STATUS_FILE, TRANSCRIPT_FILE))
  }

  @Test
  fun `should delete the local audio of an old recording that has no episode while raw audio is dropped`() {
    val directory = recording("2026-09-05-1200-tarkade-klubi", startedAt = 1788253200.0)
    storage(dropRaw = true).store()
    expect(Files.exists(directory.resolve("raw.mp3"))).toEqual(false)
  }

  @Test
  fun `should keep the raw audio of an old recording that has no episode in the bucket while raw audio is dropped`() {
    recording("2026-09-06-1200-tarkade-klubi", startedAt = 1788253200.0)
    storage(dropRaw = true).store()
    expect(keys("2026-09-06-1200-tarkade-klubi").map { it.substringAfter('/') }).toEqual(listOf("raw.mp3", STATUS_FILE, TRANSCRIPT_FILE))
  }

  @Test
  fun `should upload the raw audio of a programme kept with music and ads`() {
    programme { uncutSince = Instant.EPOCH }
    recording("2026-10-10-1200-tarkade-klubi")
    storage(dropRaw = true).store()
    expect(keys("2026-10-10-1200-tarkade-klubi").map { it.substringAfter('/') }).toEqual(listOf("raw.mp3", STATUS_FILE, TRANSCRIPT_FILE))
  }

  @Test
  fun `should delete the raw audio and fingerprints of a cut recording a day after it ended but keep its episode`() {
    val directory = cut("2026-10-11-1200-tarkade-klubi")
    Files.writeString(directory.resolve("raw-part-001.mp3"), "Kuku öö")
    sweep(dropRaw = true)
    val files = listOf("raw.mp3", "raw-part-001.mp3", FINGERPRINTS, EPISODE_FILE)
    expect(files.map { Files.exists(directory.resolve(it)) }).toEqual(listOf(false, false, false, true))
  }

  @Test
  fun `should keep the raw audio of a cut recording younger than a day`() {
    val directory = cut("2026-10-13-1200-tarkade-klubi", hours = 23)
    sweep(dropRaw = true)
    expect(Files.exists(directory.resolve("raw.mp3"))).toEqual(true)
  }

  @Test
  fun `should keep the raw audio of a recording with a programme left uncut`() {
    val shows = listOf("Tarkade klubi", "Keskpäevatund")
    val directory = cut("2026-10-14-1200-tarkade-klubi", programmes = shows, found = shows.take(1))
    sweep(dropRaw = true)
    expect(Files.exists(directory.resolve("raw.mp3"))).toEqual(true)
  }

  @Test
  fun `should keep the raw audio behind a review render left pending for a day`() {
    val directory = cut("2026-10-22-1200-tarkade-klubi", hours = 50)
    val review = directory.resolve(REVIEW_FILE)
    Files.writeString(review, "[]")
    Files.setLastModifiedTime(review, FileTime.from(Instant.now(clock).minus(25, ChronoUnit.HOURS)))
    sweep(dropRaw = true)
    expect(Files.exists(directory.resolve("raw.mp3"))).toEqual(true)
  }

  @Test
  fun `should keep the raw audio of a programme kept with music and ads`() {
    programme { uncutSince = Instant.EPOCH }
    val directory = cut("2026-10-16-1200-tarkade-klubi")
    sweep(dropRaw = true)
    expect(Files.exists(directory.resolve("raw.mp3"))).toEqual(true)
  }

  @Test
  fun `should store the raw audio of a recording whose programme has since been renamed`() {
    programme { name = "Tarkade klubi õhtul" }
    cut("2026-10-23-1200-tarkade-klubi")
    sweep(dropRaw = true)
    expect(keys("2026-10-23-1200-tarkade-klubi")).toContain("2026-10-23-1200-tarkade-klubi/raw.mp3")
  }

  @Test
  fun `should keep the raw audio of a recording whose episode is not stored`() {
    val directory = cut("2026-10-17-1200-tarkade-klubi")
    storage(dropRaw = true).drop()
    expect(Files.exists(directory.resolve("raw.mp3"))).toEqual(true)
  }

  @Test
  fun `should keep the raw audio when raw audio is not dropped`() {
    val directory = cut("2026-10-18-1200-tarkade-klubi")
    sweep(dropRaw = false)
    expect(Files.exists(directory.resolve("raw.mp3"))).toEqual(true)
  }

  @Test
  fun `should remove the raw audio already in the bucket when it drops a recording`() {
    uploaded("2026-10-19-1200-tarkade-klubi")
    storage(dropRaw = true).drop()
    val stored = keys("2026-10-19-1200-tarkade-klubi").map { it.substringAfter('/') }
    expect(stored).toEqual(listOf(EDIT_LIST_FILE, EPISODE_FILE, STATUS_FILE, TRANSCRIPT_FILE))
  }

  @Test
  fun `should drop the raw audio of other recordings when one cannot be read`() {
    val broken = Files.createDirectories(radioProperties.recordings.resolve("2026-10-20-1200-katki"))
    Files.writeString(broken.resolve(STATUS_FILE), "{")
    Files.writeString(broken.resolve("raw.mp3"), "Kuku katki")
    val directory = cut("2026-10-21-1200-tarkade-klubi")
    sweep(dropRaw = true)
    expect(Files.exists(directory.resolve("raw.mp3"))).toEqual(false)
  }

  @Test
  fun `should remove the raw audio from the bucket when it drops again after a failed drop`() {
    uploaded("2026-10-24-1200-tarkade-klubi")
    runCatching { failing().drop() }
    storage(dropRaw = true).drop()
    expect(keys("2026-10-24-1200-tarkade-klubi")).notToContain("2026-10-24-1200-tarkade-klubi/raw.mp3")
  }

  @Test
  fun `should fail when it cannot drop the raw audio of a recording`() {
    uploaded("2026-10-25-1200-tarkade-klubi")
    expect { failing().drop() }.toThrow<IllegalStateException>()
  }

  @Test
  fun `should wait before it tries again to drop raw audio it could not drop`() {
    uploaded("2026-10-26-1200-tarkade-klubi")
    val storage = failing()
    runCatching { storage.drop() }
    expect(storage.due()).toEqual(emptyList())
  }

  @Test
  fun `should try again an hour later to drop raw audio it could not drop`() {
    val directory = uploaded("2026-10-27-1200-tarkade-klubi")
    now = Instant.now(clock)
    val storage = failing(moving)
    runCatching { storage.drop() }
    now = now.plus(1, ChronoUnit.HOURS)
    expect(storage.due()).toEqual(listOf(directory))
  }

  @Test
  fun `should wait before it reads again a recording it could not read`() {
    val directory = cut("2026-10-30-1200-tarkade-klubi")
    val storage = storage(dropRaw = true).apply { store() }
    val status = Files.readString(directory.resolve(STATUS_FILE))
    Files.writeString(directory.resolve(STATUS_FILE), "{")
    storage.due()
    Files.writeString(directory.resolve(STATUS_FILE), status)
    expect(storage.due()).toEqual(emptyList())
  }

  @Test
  fun `should leave the raw audio of a recording that is being read`() {
    val directory = cut("2026-10-28-1200-tarkade-klubi")
    val storage = storage(dropRaw = true).apply { store() }
    storage.hold { storage.drop() }
    expect(Files.exists(directory.resolve("raw.mp3"))).toEqual(true)
  }

  @Test
  fun `should keep the pcm of a recording that is being read`() {
    val directory = recording("2026-10-29-1200-tarkade-klubi")
    Files.writeString(directory.resolve("episode-failed.json"), "{}")
    val storage = storage(dropRaw = true)
    storage.hold { storage.store() }
    expect(Files.exists(directory.resolve("raw.pcm"))).toEqual(true)
  }
}
