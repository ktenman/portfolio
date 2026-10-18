package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.RadioAiring
import ee.tenman.portfolio.domain.RadioProgramme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tools.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

class RadioEpisodeServiceTest {
  @TempDir
  lateinit var directory: Path

  private val outlineService = mockk<RadioOutlineService>()

  private val service =
    RadioEpisodeService(
      outlineService,
      RadioCutService(),
      RadioRenderService(),
      airings(),
      programmes(),
      RadioProperties(),
      Clock.fixed(Instant.parse("2026-10-17T11:10:00Z"), ZoneOffset.UTC),
    )

  private fun outline(
    before: Double,
    first: Double,
    end: Double,
  ) = OutlineRead(
    listOf("Ainar Ruussaar"),
    ShowStart(before, first, false, "Tere."),
    emptyList(),
    ShowEnd(end, "Head aega."),
    listOf(OutlineChapter(first, "Ooperikriis")),
  )

  private fun transcribed(vararg starts: Double) {
    val transcript =
      Transcript(starts.map { TranscriptSegment(it, it + 1, "Tere päevast.") }, starts.map { TranscriptWord(it, it + 1, " Tere") })
    Files.writeString(directory.resolve("transcript.json"), JsonMapperFactory.instance.writeValueAsString(transcript))
  }

  private fun show(state: String = "captured") {
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, taped(directory, "raw", 60))), state)
    transcribed(1.0, 5.0, 50.0)
  }

  private fun saved(vararg reads: OutlineRead?) {
    Files.writeString(directory.resolve("outline.json"), JsonMapperFactory.instance.writeValueAsString(reads.toList()))
  }

  private fun edit(): EditList = JsonMapperFactory.instance.readValue(Files.readString(directory.resolve("edit-list.json")))

  private fun failure(): String = Files.readString(directory.resolve("episode-failed.json"))

  @Test
  fun `should leave an existing episode alone`() {
    Files.writeString(directory.resolve("episode.m4a"), "valmis")
    service.make(directory)
    expect(Files.readString(directory.resolve("episode.m4a"))).toEqual("valmis")
  }

  @Test
  fun `should skip a recording that is still recording`() {
    show("recording")
    service.make(directory)
    expect(Files.exists(directory.resolve("episode-failed.json"))).toEqual(false)
  }

  @Test
  fun `should read the outline three times and render the show`() {
    show()
    every { outlineService.reads(any(), any(), AiModel.GEMINI_3_5_FLASH_LITE) } returns List(3) { outline(1.0, 5.0, 50.0) }
    service.make(directory)
    val found = edit()
    expect(Triple(found.outline, found.keep.first().start, abs(found.keep.first().end - 57.2) <= 0.05))
      .toEqual(Triple(OutlineSummary("google/gemini-3.5-flash-lite", 3, 3, 3), 4.7, true))
  }

  @Test
  fun `should reuse saved reads instead of asking again`() {
    show()
    saved(outline(1.0, 5.0, 50.0))
    service.make(directory)
    verify(exactly = 0) { outlineService.reads(any(), any(), any()) }
  }

  @Test
  fun `should vote with the reads that came back when one request fails`() {
    show()
    every { outlineService.reads(any(), any(), any()) } returns listOf(null, outline(1.0, 5.0, 50.0), outline(1.0, 5.0, 50.0))
    service.make(directory)
    val reads = JsonMapperFactory.instance.readValue<List<OutlineRead?>>(Files.readString(directory.resolve("outline.json")))
    expect(Triple(reads.first(), edit().outline.matched, Files.exists(directory.resolve("episode.m4a")))).toEqual(Triple(null, 2, true))
  }

  @Test
  fun `should save no reads and mark the recording failed when every read fails`() {
    show()
    every { outlineService.reads(any(), any(), any()) } returns listOf(null, null, null)
    service.make(directory)
    expect(Files.exists(directory.resolve("outline.json")) to ("No outline read came back" in failure())).toEqual(false to true)
  }

  @Test
  fun `should mark the recording failed naming the outline file when no read matches`() {
    show()
    saved(outline(1.0, 15.0, 50.0))
    service.make(directory)
    expect(failure()).toContain("outline.json")
  }

  @Test
  fun `should measure chapters and duration without the gap no part covers`() {
    val parts =
      listOf(
        FixturePart("raw.mp3", 0.0, taped(directory, "raw", 30)),
        FixturePart("raw-part-002.mp3", 40.0, taped(directory, "raw-part-002", 20)),
      )
    writeRecording(directory, parts, "partial")
    transcribed(1.0, 5.0, 50.0)
    saved(outline(1.0, 5.0, 50.0))
    service.make(directory)
    val found = probe(directory.resolve("episode.m4a"))
    val duration = found["format"]["duration"].asString().toDouble()
    val last = found["chapters"].toList().last()
    val end = last["end_time"].asString().toDouble()
    expect((abs(edit().durationSeconds - duration) <= 0.1) to (abs(end - duration) <= 0.1)).toEqual(true to true)
  }

  @Test
  fun `should skip a recording whose transcript is not finished`() {
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, taped(directory, "raw", 60))), "captured")
    service.make(directory)
    verify(exactly = 0) { outlineService.reads(any(), any(), any()) }
  }

  @Test
  fun `should leave a recording marked failed alone`() {
    show()
    Files.writeString(directory.resolve("episode-failed.json"), "{}")
    service.make(directory)
    verify(exactly = 0) { outlineService.reads(any(), any(), any()) }
  }

  @Test
  fun `should leave a recording of another programme alone`() {
    show()
    val status = readStatus(directory).copy(programmes = listOf("Tarkade klubi"))
    Files.writeString(directory.resolve("status.json"), JsonMapperFactory.instance.writeValueAsString(status))
    service.make(directory)
    verify(exactly = 0) { outlineService.reads(any(), any(), any()) }
  }

  @Test
  fun `should ask for no reads when it cannot write into the recording`() {
    show()
    Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("r-xr-xr-x"))
    service.make(directory)
    verify(exactly = 0) { outlineService.reads(any(), any(), any()) }
  }

  @Test
  fun `should remove a partial episode when the render fails`() {
    show()
    saved(outline(1.0, 5.0, 50.0))
    Files.writeString(directory.resolve("raw.mp3"), "katkine")
    Files.writeString(directory.resolve("episode.tmp.m4a"), "pooleli")
    service.make(directory)
    expect(Files.exists(directory.resolve("episode.tmp.m4a"))).toEqual(false)
  }

  private fun rendered(): EditList {
    show()
    saved(outline(1.0, 5.0, 50.0))
    service.make(directory)
    return edit()
  }

  @Test
  fun `should store the outline topics on the recording clock`() {
    expect(rendered().topics).toEqual(listOf(EpisodeChapter(5.0, "Ooperikriis")))
  }

  @Test
  fun `should render the reviewed keep list and mark the edit list reviewed`() {
    rendered()
    service.review(directory, listOf(KeptSpan(10.0, 20.0), KeptSpan(30.0, 40.0)))
    val duration = probe(directory.resolve("episode.m4a"))["format"]["duration"].asString().toDouble()
    expect(edit().reviewed to (abs(duration - 20) <= 0.1)).toEqual(true to true)
  }

  @Test
  fun `should keep the machine boundaries when the cuts are reviewed`() {
    val machine = rendered().boundaries
    service.review(directory, listOf(KeptSpan(10.0, 20.0)))
    expect(edit().boundaries).toEqual(machine)
  }

  @Test
  fun `should reject a keep list whose spans overlap`() {
    rendered()
    expect { service.review(directory, listOf(KeptSpan(10.0, 30.0), KeptSpan(20.0, 40.0))) }
      .toThrow<IllegalArgumentException>()
      .messageToContain("Keep list")
  }

  @Test
  fun `should end a keep list that reaches past the recording at its last sample`() {
    rendered()
    service.review(directory, listOf(KeptSpan(50.0, 60.04)))
    expect(edit().keep).toEqual(listOf(KeptSpan(50.0, 60.0)))
  }

  @Test
  fun `should reject a keep list whose spans touch`() {
    rendered()
    expect { service.review(directory, listOf(KeptSpan(10.0, 20.0), KeptSpan(20.0, 30.0))) }.toThrow<IllegalArgumentException>()
  }

  @Test
  fun `should reject a kept span that no part covers`() {
    val parts =
      listOf(
        FixturePart("raw.mp3", 0.0, taped(directory, "raw", 30)),
        FixturePart("raw-part-002.mp3", 40.0, taped(directory, "raw-part-002", 20)),
      )
    writeRecording(directory, parts, "partial")
    transcribed(1.0, 5.0, 50.0)
    saved(outline(1.0, 5.0, 50.0))
    service.make(directory)
    expect { service.review(directory, listOf(KeptSpan(10.0, 20.0), KeptSpan(32.0, 38.0))) }.toThrow<IllegalArgumentException>()
  }

  @Test
  fun `should reject an empty keep list`() {
    rendered()
    expect { service.review(directory, emptyList()) }.toThrow<IllegalArgumentException>()
  }

  @Test
  fun `should end a break at a saved jingle`() {
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, taped(directory, "raw", 60))))
    transcribed(1.0, 5.0, 12.0, 30.0, 50.0)
    saved(outline(1.0, 5.0, 50.0).copy(breaks = listOf(OutlineBreak(12.0, 30.0, "Väike paus."))))
    val properties = RadioProperties(recordings = directory.resolve("arhiiv"))
    store(pcmOf(properties.jingles, "2026-10-17-1"), template(timeline(directory), 17.0))
    RadioEpisodeService(
      outlineService,
      RadioCutService(),
      RadioRenderService(),
      airings(),
      programmes(),
      properties,
      Clock.systemUTC(),
    ).make(directory)
    expect(edit().boundaries[2].jingle?.saved).toEqual("2026-10-17-1")
  }

  private fun shared() {
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, taped(directory, "raw", 120))))
    save(directory.resolve("status.json"), readStatus(directory).copy(seconds = 120.0, programmes = listOf(PROGRAMME, "Tarkade klubi")))
    transcribed(1.0, 5.0, 50.0, 65.0, 70.0, 110.0)
    saved(outline(1.0, 5.0, 50.0))
  }

  private fun asked(read: OutlineRead) {
    save(directory.resolve("cuts-requested.json"), CutRequest(listOf("Tarkade klubi")))
    Files.writeString(directory.resolve("outline-2.json"), JsonMapperFactory.instance.writeValueAsString(listOf(read)))
  }

  @Test
  fun `should cut only the programme it knows in a recording shared with another`() {
    shared()
    service.make(directory)
    expect(edit().found() to Files.exists(directory.resolve("episode.m4a"))).toEqual(setOf(PROGRAMME) to true)
  }

  @Test
  fun `should find the cuts of another programme on request without rendering them`() {
    shared()
    service.make(directory)
    val episode = Files.readAllBytes(directory.resolve("episode.m4a"))
    asked(outline(65.0, 70.0, 110.0))
    service.make(directory)
    expect(edit().found() to Files.readAllBytes(directory.resolve("episode.m4a")).contentEquals(episode))
      .toEqual(setOf(PROGRAMME, "Tarkade klubi") to true)
  }

  @Test
  fun `should cut between two programmes found in one recording`() {
    shared()
    service.make(directory)
    asked(outline(65.0, 70.0, 110.0))
    service.make(directory)
    val between = cuts(edit(), 120.0).single { it.kind == CutKind.BETWEEN }
    expect(between.start to between.end).toEqual(57.2 to 69.7)
  }

  @Test
  fun `should not render a recording whose only programme was found on request`() {
    shared()
    save(directory.resolve("status.json"), readStatus(directory).copy(programmes = listOf("Tarkade klubi", "Ilmaparandaja")))
    Files.writeString(directory.resolve("outline.json"), JsonMapperFactory.instance.writeValueAsString(listOf(outline(65.0, 70.0, 110.0))))
    save(directory.resolve("cuts-requested.json"), CutRequest(listOf("Tarkade klubi")))
    service.make(directory)
    expect(edit().found() to Files.exists(directory.resolve("episode.m4a"))).toEqual(setOf("Tarkade klubi") to false)
  }

  @Test
  fun `should mark the recording failed when two programmes overlap`() {
    shared()
    asked(outline(1.0, 5.0, 50.0))
    service.make(directory)
    expect(failure()).toContain("Programmes overlap")
  }

  @Test
  fun `should ask for the outline of a programme with its air time from the schedule`() {
    shared()
    save(directory.resolve("cuts-requested.json"), CutRequest(listOf("Tarkade klubi")))
    val klubi = RadioProgramme(231, "Tarkade klubi", "tarkade-klubi").apply { id = 7 }
    val airing = RadioAiring(9, 7, "Tarkade klubi", Instant.ofEpochSecond(60), Instant.ofEpochSecond(115))
    every { outlineService.reads(any(), any(), any()) } returns listOf(outline(65.0, 70.0, 110.0))
    val clock = Clock.systemUTC()
    RadioEpisodeService(
      outlineService,
      RadioCutService(),
      RadioRenderService(),
      airings(airing),
      programmes(klubi),
      RadioProperties(),
      clock,
    ).make(directory)
    verify { outlineService.reads(Show("Tarkade klubi", Slot(60, 115), 0.0, 120.0), any(), any()) }
  }

  @Test
  fun `should find no review for a recording without an edit list`() {
    show()
    expect(service.review(directory, listOf(KeptSpan(10.0, 20.0)))).toEqual(null)
  }
}
