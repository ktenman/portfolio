package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.JsonMapperFactory
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RadioOutlineTest {
  private fun outline(
    before: Double,
    first: Double,
    breaks: List<Pair<Double, Double>>,
    end: Double,
    music: Boolean = true,
    hosts: List<String> = listOf("Ainar Ruussaar"),
  ) = OutlineRead(
    hosts,
    ShowStart(before, first, music, "Tere."),
    breaks.map { OutlineBreak(it.first, it.second, "Väike paus.") },
    ShowEnd(end, "Kohtume taas."),
    listOf(OutlineChapter(first, "Rahvusooper Estonia")),
  )

  private fun linesAt(vararg starts: Double) = starts.map { TranscriptLine(it, it + 1, "lause", emptyList()) }

  @Test
  fun `should take the lower median of the reads that agree on the break count`() {
    val reads =
      listOf(
        outline(5.0, 10.0, listOf(30.0 to 40.0, 60.0 to 70.0), 90.0),
        outline(5.0, 12.0, listOf(30.0 to 40.0, 60.0 to 70.0), 90.0),
        outline(5.0, 11.0, listOf(30.0 to 40.0), 90.0),
      )
    val agreed = consensus(reads)
    expect(agreed.firstSpeechTime to agreed.voters).toEqual(10.0 to 2)
  }

  @Test
  fun `should use the first read when every break count differs`() {
    val reads =
      listOf(
        outline(5.0, 12.0, listOf(30.0 to 40.0), 90.0),
        outline(5.0, 10.0, emptyList(), 90.0),
        outline(5.0, 11.0, listOf(30.0 to 40.0, 60.0 to 70.0), 90.0),
      )
    val agreed = consensus(reads)
    expect(Triple(agreed.firstSpeechTime, agreed.breaks, agreed.voters)).toEqual(Triple(12.0, listOf(Pause(30.0, 40.0)), 1))
  }

  @Test
  fun `should take the music flag of the majority`() {
    val reads =
      listOf(
        outline(5.0, 10.0, emptyList(), 90.0, music = false),
        outline(5.0, 10.0, emptyList(), 90.0),
        outline(5.0, 10.0, emptyList(), 90.0),
      )
    expect(consensus(reads).opensWithMusic).toEqual(true)
  }

  @Test
  fun `should take hosts and topics from the first voter`() {
    val reads =
      listOf(
        outline(5.0, 10.0, emptyList(), 90.0, hosts = listOf("Hindrek Riikoja")),
        outline(5.0, 10.0, emptyList(), 90.0, hosts = listOf("Indrek Riikoja")),
      )
    expect(consensus(reads).hosts).toEqual(listOf("Hindrek Riikoja"))
  }

  @Test
  fun `should refuse to vote without reads`() {
    expect { consensus(emptyList()) }.toThrow<IllegalArgumentException>().messageToContain("No outline read")
  }

  @Test
  fun `should reject a read whose answer starts no line`() {
    expect(match(outline(5.0, 50.0, listOf(70.0 to 80.0), 90.0), linesAt(5.0, 48.5, 70.0, 80.0, 90.0))).toEqual(null)
  }

  @Test
  fun `should reject a read that lacks a field`() {
    val read = JsonMapperFactory.instance.writeValueAsString(outline(5.0, 50.0, emptyList(), 90.0))
    val broken = read.replace(Regex(""","showEnd":\{[^}]*}"""), "")
    expect(parse(broken)).toEqual(null)
  }

  @Test
  fun `should reject a read whose time is missing`() {
    val read = JsonMapperFactory.instance.writeValueAsString(outline(5.0, 50.0, emptyList(), 90.0))
    val broken = read.replace(""""lastShowLineTime":90.0,""", "")
    expect(parse(broken)).toEqual(null)
  }

  @Test
  fun `should move each answer to the line starting within a second`() {
    val matched = match(outline(5.0, 50.0, listOf(70.0 to 80.0), 90.0), linesAt(5.04, 49.2, 50.3, 70.0, 80.0, 90.0))
    val start = matched?.showStart
    expect(listOf(start?.lastNonShowLineTime, start?.firstSpeechTime, matched?.chapters?.first()?.time)).toEqual(listOf(5.04, 50.3, 50.3))
  }

  @Test
  fun `should give each segment its words`() {
    val transcript =
      Transcript(
        listOf(TranscriptSegment(1.0, 2.0, "Tere."), TranscriptSegment(3.0, 4.5, "Õhtust küll.")),
        listOf(TranscriptWord(1.0, 2.0, " Tere."), TranscriptWord(3.0, 3.6, " Õhtust"), TranscriptWord(3.7, 4.5, " küll.")),
      )
    expect(lines(transcript).map { line -> line.words.map { it.word } }).toEqual(listOf(listOf(" Tere."), listOf(" Õhtust", " küll.")))
  }

  @Test
  fun `should print events and lines on the stream clock`() {
    val started = ZonedDateTime.of(2026, 10, 17, 11, 55, 0, 0, ZoneId.of("Europe/Tallinn")).toEpochSecond().toDouble()
    val text =
      describe(
        RecordingStatus(RecordingState.CAPTURED, started, 4200.0),
        Show(PROGRAMME, null, 0.0, 4200.0),
        listOf(TranscriptLine(25.24, 27.8, "Siin saade on tänaseks kuulatud.", emptyList())),
        listOf(AudioEvent(66.5, 67.36, "digital silence", -25.2, -13.4)),
      )
    val event = "  66.50-  67.36 s (11:56:06)  digital silence  before  -25.2 dB  after  -13.4 dB"
    expect(Triple(text.lines().first(), event in text, text.trimEnd().lines().last()))
      .toEqual(
        Triple(
          "Recording: Kuku Raadio live internet stream, Saturday 17.10.2026, 70 minutes, stream clock 11:55:00-13:05:00.",
          true,
          "   25.2-   27.8 s (11:55:25)  Siin saade on tänaseks kuulatud.",
        ),
      )
  }

  @Test
  fun `should name the programme and its air time from the schedule`() {
    val started = ZonedDateTime.of(2026, 10, 24, 11, 55, 0, 0, ZoneId.of("Europe/Tallinn")).toEpochSecond()
    val status = RecordingStatus(RecordingState.CAPTURED, started.toDouble(), 7800.0, programmes = listOf(PROGRAMME, "Tarkade klubi"))
    val text = describe(status, show(status, "Tarkade klubi", Slot(started + 3900, started + 7500)), emptyList(), emptyList())
    expect(text.lines().take(2)).toEqual(
      listOf(
        "Recording: Kuku Raadio live internet stream, Saturday 24.10.2026, 66 minutes, stream clock 12:57:00-14:03:00.",
        "The published schedule lists the talk show \"Tarkade klubi\" for 13:00-14:00. The stream lags the broadcast by about 20 s.",
      ),
    )
  }

  @Test
  fun `should give the outline only the lines inside the window of its programme`() {
    val lines =
      listOf(
        TranscriptLine(100.0, 104.0, "Keskpäevatund algab.", emptyList()),
        TranscriptLine(4000.0, 4004.0, "Tarkade klubi!", emptyList()),
      )
    val text =
      describe(RecordingStatus(RecordingState.CAPTURED, 0.0, 7800.0), Show("Tarkade klubi", null, 3720.0, 7680.0), lines, emptyList())
    expect(("Tarkade klubi!" in text) to ("Keskpäevatund algab." in text)).toEqual(true to false)
  }

  @Test
  fun `should window a programme of a shared recording three minutes around its air time`() {
    val status = RecordingStatus(RecordingState.CAPTURED, 1000.0, 7800.0, programmes = listOf(PROGRAMME, "Tarkade klubi"))
    expect(show(status, PROGRAMME, Slot(1300, 4900))).toEqual(Show(PROGRAMME, Slot(1300, 4900), 120.0, 4080.0))
  }

  @Test
  fun `should give the whole recording of a single programme to its outline`() {
    val status = RecordingStatus(RecordingState.CAPTURED, 1000.0, 4200.0)
    expect(show(status, PROGRAMME, Slot(1300, 4900))).toEqual(Show(PROGRAMME, Slot(1300, 4900), 0.0, 4200.0))
  }

  @Test
  fun `should take a line in another script for music`() {
    val lines = listOf(TranscriptLine(10.0, 14.0, "Мы любим оперу и буфет", emptyList()))
    expect(music(lines, emptyList()).single().music).toEqual(true)
  }

  @Test
  fun `should keep a spoken line that names someone in another script`() {
    val lines = listOf(TranscriptLine(10.0, 14.0, "Täna räägime sellest, mida ütles Зеленський", emptyList()))
    expect(music(lines, emptyList()).single().music).toEqual(false)
  }

  @Test
  fun `should print a level with no audio around it as nan`() {
    val events = listOf(AudioEvent(66.5, 67.36, "digital silence", -25.2, Double.NaN))
    expect(
      "after    nan dB" in
        describe(RecordingStatus(RecordingState.PARTIAL, 0.0, 67.36), Show(PROGRAMME, null, 0.0, 67.36), emptyList(), events),
    ).toEqual(true)
  }
}
