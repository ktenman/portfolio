package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
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
      mockk { every { resource(any(), any()) } returns null },
      RadioProperties(recordings = recordings),
      Clock.fixed(Instant.parse("2026-10-18T10:00:00Z"), ZoneOffset.UTC),
    )
  }

  private fun recording(
    name: String,
    state: RecordingState = RecordingState.CAPTURED,
    programmes: List<String> = listOf(PROGRAMME),
  ): Path {
    val directory = Files.createDirectories(recordings.resolve(name))
    val status = RecordingStatus(state, 1792230900.0, 4200.0, listOf(RecordingPart("raw.mp3", 0.0)), programmes)
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

  private val shared = listOf(PROGRAMME, "Tarkade klubi")

  @Test
  fun `should ask for the cuts of a programme in a recording`() {
    val directory = recording("2026-10-24-1155-keskpaevatund-tarkade-klubi", programmes = shared)
    service.find(directory.fileName.toString(), CutChoice("Tarkade klubi"))
    expect(read<CutRequest>(directory.resolve(CUT_REQUEST))).toEqual(CutRequest(listOf("Tarkade klubi")))
  }

  @Test
  fun `should refuse the cuts of a programme the recording does not hold`() {
    recording("2026-10-24-1155-keskpaevatund")
    expect { service.find("2026-10-24-1155-keskpaevatund", CutChoice("Tarkade klubi")) }.toThrow<IllegalArgumentException>()
  }

  @Test
  fun `should find no recording to ask cuts of under an unknown name`() {
    expect(service.find("2026-10-24-1155-keskpaevatund", CutChoice(PROGRAMME))).toEqual(false)
  }

  @Test
  fun `should try a failed recording again when its cuts are asked for`() {
    val directory = recording("2026-10-24-1155-keskpaevatund-tarkade-klubi", programmes = shared)
    Files.writeString(directory.resolve("episode-failed.json"), """{"reason":"Katki","failedAt":"2026-10-24T11:10:00Z"}""")
    service.find(directory.fileName.toString(), CutChoice("Tarkade klubi"))
    expect(Files.exists(directory.resolve("episode-failed.json"))).toEqual(false)
  }

  @Test
  fun `should show which programmes of a recording have their cuts looked for`() {
    val directory = recording("2026-10-24-1155-keskpaevatund-tarkade-klubi-ilmaparandaja", programmes = shared + "Ilmaparandaja")
    service.find(directory.fileName.toString(), CutChoice("Ilmaparandaja"))
    expect(
      service
      .recordings()
      .single()
      .programmeCuts
      .map { it.state },
        ).toEqual(
      listOf(ProgrammeCutState.FINDING, ProgrammeCutState.NONE, ProgrammeCutState.FINDING),
    )
  }

  @Test
  fun `should offer to look for the cuts of a failed recording again`() {
    val directory = recording("2026-10-24-1155-keskpaevatund")
    Files.writeString(directory.resolve("episode-failed.json"), """{"reason":"Katki","failedAt":"2026-10-24T11:10:00Z"}""")
    expect(service.recordings().single().programmeCuts).toEqual(listOf(ProgrammeCuts(PROGRAMME, ProgrammeCutState.NONE)))
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
  fun `should read the format of the first part from its probe`() {
    val probe = """{"streams":[{"codec_name":"mp3","bit_rate":"256000","channels":2}]}"""
    Files.writeString(recording("2026-10-17").resolve("raw.probe.json"), probe)
    expect(service.recordings().single().format).toEqual(AudioFormat("mp3", 256_000))
  }

  @Test
  fun `should count a drop of the stream for each part after the first`() {
    val directory = recording("2026-10-17")
    val parts = listOf(RecordingPart("raw.mp3", 0.0), RecordingPart("raw-part-002.mp3", 9.5), RecordingPart("raw-part-003.mp3", 19.0))
    val status = RecordingStatus(RecordingState.CAPTURED, 1792230900.0, 4200.0, parts)
    Files.writeString(directory.resolve(STATUS_FILE), JsonMapperFactory.instance.writeValueAsString(status))
    expect(service.recordings().single().drops).toEqual(2)
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

  private fun jingled(saved: String? = null): Path {
    val directory = Files.createDirectories(recordings.resolve("2026-10-17"))
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, noise(10.0))))
    val boundaries =
      listOf(
        Boundary("show starts", 1.0, "digital silence", false),
        Boundary("break 1 starts", 3.0, "digital silence", false),
        Boundary("break 1 ends", 6.0, "station jingle, r=0.93", false, JingleMatch(6.5, 0.93, saved)),
      )
    edited(directory, listOf(KeptSpan(1.0, 3.0), KeptSpan(6.0, 10.0)), boundaries)
    return directory
  }

  private fun jingles(): Path = recordings.resolve("jingles")

  private fun pause(): Cut = service.recordings().single().cuts[1]

  @Test
  fun `should save the jingle that ends a break from the recording`() {
    val directory = jingled()
    service.jingle("2026-10-17", 1, JingleReview(true))
    expect(library(jingles()).single().template.toList()).toEqual(timeline(directory).read(sample(6.5), RATE).toList())
  }

  @Test
  fun `should name a saved jingle after its recording and cut`() {
    jingled()
    service.jingle("2026-10-17", 1, JingleReview(true))
    expect(library(jingles()).map { it.name }).toEqual(listOf("2026-10-17-1"))
  }

  @Test
  fun `should offer to save the jingle that ends a break`() {
    jingled()
    expect(pause().jingle).toEqual(CutJingle(0.93, false))
  }

  @Test
  fun `should mark a cut whose jingle is saved`() {
    jingled()
    service.jingle("2026-10-17", 1, JingleReview(true))
    expect(pause().jingle).toEqual(CutJingle(0.93, true))
  }

  @Test
  fun `should mark a cut that matched a jingle saved from another recording`() {
    jingled("2026-10-10-1")
    store(pcmOf(jingles(), "2026-10-10-1"), DoubleArray(RATE))
    expect(pause().jingle).toEqual(CutJingle(0.93, true))
  }

  @Test
  fun `should never write over a saved jingle`() {
    jingled()
    store(pcmOf(jingles(), "2026-10-17-1"), DoubleArray(RATE) { 5.0 })
    service.jingle("2026-10-17", 1, JingleReview(true))
    expect(library(jingles()).single().template.distinct()).toEqual(listOf(5.0))
  }

  @Test
  fun `should forget a saved jingle`() {
    jingled()
    service.jingle("2026-10-17", 1, JingleReview(true))
    service.jingle("2026-10-17", 1, JingleReview(false))
    expect(library(jingles())).toEqual(emptyList())
  }

  @Test
  fun `should find no jingle at a cut that has none`() {
    jingled()
    expect(service.jingle("2026-10-17", 0, JingleReview(true))).toEqual(false)
  }

  @Test
  fun `should find no jingle at a cut the recording does not have`() {
    jingled()
    expect(service.jingle("2026-10-17", 7, JingleReview(true))).toEqual(false)
  }

  @Test
  fun `should find no jingle in a recording without an edit list`() {
    recording("2026-10-17")
    expect(service.jingle("2026-10-17", 1, JingleReview(true))).toEqual(false)
  }

  private fun quiet(): Path {
    val directory = Files.createDirectories(recordings.resolve("2026-10-24"))
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, noise(60.0, listOf(20.0, 40.0)))))
    val status = RecordingStatus(RecordingState.CAPTURED, 1792832100.0, 60.0, listOf(RecordingPart("raw.mp3", 0.0)))
    Files.writeString(directory.resolve(STATUS_FILE), JsonMapperFactory.instance.writeValueAsString(status))
    edited(directory, listOf(KeptSpan(5.0, 60.0)), listOf(Boundary("show starts", 5.0, "digital silence", false)))
    return directory
  }

  private fun manual(): List<ManualCut> =
    JsonMapperFactory.instance.readValue(Files.readString(recordings.resolve("2026-10-24/edit-list.json")), EditList::class.java).manual

  @Test
  fun `should add a cut by hand with its ends at the nearest silences`() {
    quiet()
    service.add("2026-10-24", ManualCut(19.0, 41.0))
    expect(manual()).toEqual(listOf(ManualCut(20.15, 40.15)))
  }

  @Test
  fun `should refuse a cut by hand that overlaps a cut`() {
    quiet()
    expect { service.add("2026-10-24", ManualCut(3.0, 10.0)) }.toThrow<IllegalArgumentException>()
  }

  @Test
  fun `should drop a cut made by hand`() {
    quiet()
    service.add("2026-10-24", ManualCut(19.0, 41.0))
    service.drop("2026-10-24", 1)
    expect(manual()).toEqual(emptyList())
  }

  @Test
  fun `should not drop a machine cut`() {
    quiet()
    expect(service.drop("2026-10-24", 0)).toEqual(false)
  }

  @Test
  fun `should not drop a cut made by hand that the episode leaves out`() {
    val directory = quiet()
    edited(directory, listOf(KeptSpan(5.0, 20.0), KeptSpan(40.0, 60.0)), manual = listOf(ManualCut(20.0, 40.0)))
    expect { service.drop("2026-10-24", 1) }.toThrow<IllegalArgumentException>()
  }

  private fun edited(
    directory: Path,
    keep: List<KeptSpan> = listOf(KeptSpan(761.5, 4200.0)),
    boundaries: List<Boundary> = listOf(Boundary("show starts", 761.5, "digital silence", false)),
    music: List<KeptSpan> = emptyList(),
    manual: List<ManualCut> = emptyList(),
  ) {
    val edit =
      EditList(
        keep = keep,
        durationSeconds = 3438.5,
        parts = listOf(RecordingPart("raw.mp3", 0.0)),
        boundaries = boundaries,
        hosts = emptyList(),
        outline = OutlineSummary(null, 1, 1, 1),
        chapters = emptyList(),
        reviewed = false,
        renderedAt = "2026-10-17T10:20:00Z",
        music = music,
        manual = manual,
      )
    Files.writeString(directory.resolve("edit-list.json"), JsonMapperFactory.instance.writeValueAsString(edit))
  }

  private fun sung(): String {
    val directory = recording("2026-10-17")
    edited(directory, music = listOf(KeptSpan(761.5, 928.0)))
    val segments =
      listOf(
        TranscriptSegment(756.8, 760.9, "Välismääraja paneb kokku Neeme Raud."),
        TranscriptSegment(795.0, 805.2, "Pane ooperist ja tema hüvas puhvetist."),
        TranscriptSegment(863.8, 886.0, "Ja ooperit ja ooperit me armastame ooperit."),
        TranscriptSegment(928.0, 938.0, "Tere, siin Keskpäevatund."),
      )
    Files.writeString(directory.resolve(TRANSCRIPT_FILE), JsonMapperFactory.instance.writeValueAsString(Transcript(segments, emptyList())))
    return directory.fileName.toString()
  }

  @Test
  fun `should give the opening song as one music line`() {
    val lines = service.transcript(sung()).orEmpty()
    expect(lines.map { Triple(it.start, it.end, it.music) }).toEqual(
      listOf(Triple(756.8, 760.9, false), Triple(795.0, 886.0, true), Triple(928.0, 938.0, false)),
    )
  }

  @Test
  fun `should leave the song out of the text`() {
    expect(service.text(sung())).toEqual("Välismääraja paneb kokku Neeme Raud.\nTere, siin Keskpäevatund.\n")
  }

  @Test
  fun `should leave the song out of the subtitles`() {
    expect(service.subtitles(sung())).toEqual(
      "1\n00:12:36,800 --> 00:12:40,900\nVälismääraja paneb kokku Neeme Raud.\n\n" +
        "2\n00:15:28,000 --> 00:15:38,000\nTere, siin Keskpäevatund.\n\n",
    )
  }
}
