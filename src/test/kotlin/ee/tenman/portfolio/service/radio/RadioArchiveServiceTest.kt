package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.JsonMapperFactory
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RadioArchiveServiceTest {
  @TempDir
  lateinit var recordings: Path

  private val episodeService = mockk<RadioEpisodeService>()

  private val service by lazy {
    RadioArchiveService(
      episodeService,
      RadioProperties(recordings = recordings),
      Clock.fixed(Instant.parse("2026-10-18T10:00:00Z"), ZoneOffset.UTC),
    )
  }

  private fun recording(
    name: String,
    state: RecordingState = RecordingState.CAPTURED,
  ): Path {
    val directory = Files.createDirectories(recordings.resolve(name))
    val status = RecordingStatus(state, 1792230900.0, 4200.0, listOf(RecordingPart("raw.mp3", 0.0)))
    Files.writeString(directory.resolve(STATUS_FILE), JsonMapperFactory.instance.writeValueAsString(status))
    Files.write(directory.resolve("raw.mp3"), ByteArray(1500))
    return directory
  }

  private fun transcribed(directory: Path) {
    val transcript =
      Transcript(
        listOf(TranscriptSegment(1.0, 2.5, "Tere, Keskpäevatund!")),
        listOf(TranscriptWord(1.0, 1.4, " Tere,"), TranscriptWord(1.5, 2.5, " Keskpäevatund!")),
      )
    Files.writeString(directory.resolve(TRANSCRIPT_FILE), JsonMapperFactory.instance.writeValueAsString(transcript))
  }

  @Test
  fun `should list recordings newest first`() {
    recording("2026-10-17")
    recording("2026-10-24", RecordingState.RECORDING)
    expect(service.recordings().map { it.name }).toEqual(listOf("2026-10-24", "2026-10-17"))
  }

  @Test
  fun `should list a recording in progress without a transcript or episode`() {
    recording("2026-10-24", RecordingState.RECORDING)
    val found = service.recordings().single()
    expect(listOf(found.transcribed, found.editList, found.episodeBytes)).toEqual(listOf(false, null, null))
  }

  @Test
  fun `should give the size of the recorded parts and the episode`() {
    Files.write(recording("2026-10-17").resolve("episode.m4a"), ByteArray(700))
    val found = service.recordings().single()
    expect(found.sizeBytes to found.episodeBytes).toEqual(1500L to 700L)
  }

  @Test
  fun `should skip a directory without a status`() {
    Files.createDirectories(recordings.resolve("tühi"))
    expect(service.recordings()).toEqual(emptyList())
  }

  @Test
  fun `should list the other recordings when one has a broken edit list`() {
    Files.writeString(recording("2026-10-17").resolve("edit-list.json"), "{\"keep\":")
    recording("2026-10-24")
    expect(service.recordings().map { it.name }).toEqual(listOf("2026-10-24"))
  }

  @Test
  fun `should skip a directory whose name cannot be addressed`() {
    recording("Keskpäevatund")
    expect(service.recordings()).toEqual(emptyList())
  }

  @Test
  fun `should list nothing when the recordings directory is missing`() {
    Files.delete(recordings)
    expect(service.recordings()).toEqual(emptyList())
  }

  @Test
  fun `should give the transcript as lines with their words`() {
    transcribed(recording("2026-10-17"))
    val line = service.transcript("2026-10-17")?.single()
    expect(line?.words?.map { it.word }).toEqual(listOf(" Tere,", " Keskpäevatund!"))
  }

  @Test
  fun `should mark the transcript line the show starts inside`() {
    val directory = recording("2026-10-17")
    edited(directory)
    val transcript =
      Transcript(
        listOf(TranscriptSegment(760.0, 763.0, " Tere päevast!")),
        listOf(TranscriptWord(760.0, 761.0, " Tere"), TranscriptWord(762.0, 763.0, " päevast!")),
      )
    Files.writeString(directory.resolve(TRANSCRIPT_FILE), JsonMapperFactory.instance.writeValueAsString(transcript))
    expect(
      service
      .transcript("2026-10-17")
      ?.single()
      ?.split
      ?.kind,
        ).toEqual(CutKind.BEFORE)
  }

  @Test
  fun `should find no transcript for a recording that has none`() {
    recording("2026-10-24", RecordingState.RECORDING)
    expect(service.transcript("2026-10-24")).toEqual(null)
  }

  @Test
  fun `should refuse a name that leaves the recordings directory`() {
    transcribed(recording("2026-10-17"))
    expect(service.transcript("../${recordings.fileName}/2026-10-17")).toEqual(null)
  }

  @Test
  fun `should serve the raw audio`() {
    recording("2026-10-17")
    expect(service.file("2026-10-17", "raw.mp3")?.contentLength()).toEqual(1500L)
  }

  @Test
  fun `should serve no file outside the audio and transcripts`() {
    recording("2026-10-17")
    expect(service.file("2026-10-17", STATUS_FILE)).toEqual(null)
  }

  @Test
  fun `should serve no file that is missing`() {
    recording("2026-10-17")
    expect(service.file("2026-10-17", "episode.m4a")).toEqual(null)
  }

  @Test
  fun `should mark the transcript checked`() {
    transcribed(recording("2026-10-17"))
    service.check("2026-10-17")
    expect(service.recordings().single().transcriptCheckedAt).toEqual("2026-10-18T10:00:00Z")
  }

  @Test
  fun `should not mark a missing transcript checked`() {
    recording("2026-10-24", RecordingState.RECORDING)
    expect(service.check("2026-10-24")).toEqual(false)
  }

  @Test
  fun `should keep what lies between the removed cuts`() {
    val directory = recording("2026-10-17")
    edited(directory)
    val edit = mockk<EditList>()
    every { episodeService.review(directory, listOf(KeptSpan(761.5, 4200.0))) } returns edit
    expect(service.review("2026-10-17", CutReview(listOf(0)))).toEqual(edit)
  }

  @Test
  fun `should serve the cuts of a recording with the ones the keep list removes`() {
    edited(recording("2026-10-17"))
    expect(
      service
      .recordings()
      .single()
      .cuts
      .map { it.kind to it.removed },
        ).toEqual(listOf(CutKind.BEFORE to true))
  }

  @Test
  fun `should quote the transcript around each cut of a recording`() {
    val directory = recording("2026-10-17")
    transcribed(directory)
    edited(directory)
    expect(service.contexts("2026-10-17")?.map { it.before?.text to it.after?.text }).toEqual(listOf(null to null))
  }

  @Test
  fun `should find no cut context for a recording without a transcript`() {
    edited(recording("2026-10-17"))
    expect(service.contexts("2026-10-17")).toEqual(null)
  }

  @Test
  fun `should name the programme of a recording`() {
    recording("2026-10-17")
    expect(service.recordings().single().programme).toEqual("Keskpäevatund")
  }

  @Test
  fun `should serve the timeline of a recording`() {
    edited(recording("2026-10-17"))
    expect(
      service
      .recordings()
      .single()
      .timeline
      .map { it.title },
        ).toEqual(listOf("Enne saadet", "Keskpäevatund"))
  }

  @Test
  fun `should find no review for a recording without an edit list`() {
    recording("2026-10-17")
    expect(service.review("2026-10-17", CutReview(emptyList()))).toEqual(null)
  }

  @Test
  fun `should serve the kept audio without the gap between the parts`() {
    val directory = Files.createDirectories(recordings.resolve("2026-10-17"))
    writeRecording(
      directory,
      listOf(FixturePart("raw.mp3", 0.0, constant(10.0, 0)), FixturePart("raw-part-002.mp3", 15.0, constant(10.0, 0))),
    )
    edited(directory, listOf(KeptSpan(2.0, 22.0)))
    expect(service.recordings().single().audible).toEqual(listOf(KeptSpan(2.0, 10.0), KeptSpan(15.0, 22.0)))
  }

  @Test
  fun `should say a transcript nobody has checked is unchecked`() {
    transcribed(recording("2026-10-17"))
    expect(service.recordings().single().transcriptState).toEqual(TranscriptState.UNCHECKED)
  }

  @Test
  fun `should say a recording in progress has its transcript still coming`() {
    recording("2026-10-24", RecordingState.RECORDING)
    expect(service.recordings().single().transcriptState).toEqual(TranscriptState.RECORDING)
  }

  @Test
  fun `should say the cuts of an edit list nobody has reviewed are suggested`() {
    edited(recording("2026-10-17"))
    expect(service.recordings().single().cutState).toEqual(CutState.SUGGESTED)
  }

  @Test
  fun `should say a recording without an edit list has no cuts yet`() {
    recording("2026-10-17")
    expect(service.recordings().single().cutState).toEqual(CutState.NONE)
  }

  @Test
  fun `should find no review for an unknown recording`() {
    expect(service.review("2026-10-31", CutReview(emptyList()))).toEqual(null)
  }

  private fun edited(
    directory: Path,
    keep: List<KeptSpan> = listOf(KeptSpan(761.5, 4200.0)),
  ) {
    val edit =
      EditList(
        keep = keep,
        durationSeconds = 3438.5,
        parts = listOf(RecordingPart("raw.mp3", 0.0)),
        boundaries = listOf(Boundary("show starts", 761.5, "digital silence", false)),
        hosts = emptyList(),
        outline = OutlineSummary(null, 1, 1, 1),
        chapters = emptyList(),
        reviewed = false,
        renderedAt = "2026-10-17T10:20:00Z",
      )
    Files.writeString(directory.resolve("edit-list.json"), JsonMapperFactory.instance.writeValueAsString(edit))
  }
}
