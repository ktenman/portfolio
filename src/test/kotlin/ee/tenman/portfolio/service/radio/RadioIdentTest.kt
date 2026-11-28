package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Station
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RadioIdentTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun `should end the ident at the first quiet moment after the jingle`() {
    val tape = taped(LOUD to 10.0..15.0, HUSH to 15.0..16.0)
    expect(identCuts(tape, listOf(ended(10.0)), spoken(16.0), emptyList())).toEqual(listOf(IdentCut(SHOW, 10.0, 5.0, 0.9)))
  }

  @Test
  fun `should end the ident before the host's first word when the bed plays on`() {
    val tape = taped(LOUD to 10.0..30.0)
    expect(identCuts(tape, listOf(ended(10.0)), spoken(15.0), emptyList()).map { it.seconds }).toEqual(listOf(4.8))
  }

  @Test
  fun `should count the first word from the cut when the jingle is cut out`() {
    val tape = taped(LOUD to 10.0..30.0)
    val boundary = ended(10.0).copy(seconds = 14.0)
    expect(
      identCuts(tape, listOf(boundary), said(11.0, "Keskpäevatund") + spoken(16.0), emptyList()).map { it.seconds },
    ).toEqual(listOf(5.8))
  }

  @Test
  fun `should keep a jingle whose voice ends with the programme's name`() {
    val tape = taped(LOUD to 10.0..30.0)
    val boundary = ended(10.0).copy(seconds = 14.0)
    val words = said(10.5, "Kuku", "Raadio", "eetris", "on", "Keskpäeva", "tund.") + spoken(16.0)
    expect(identCuts(tape, listOf(boundary), words, emptyList()).map { it.seconds }).toEqual(listOf(5.8))
  }

  @Test
  fun `should skip a jingle whose voice says more than the programme's name`() {
    val tape = taped(LOUD to 10.0..30.0)
    val boundary = ended(10.0).copy(seconds = 14.0)
    val words = said(10.5, "Keskpäevatunni", "toob", "teieni", "Haus", "galerii,", "tutvu", "oksjoniga") + spoken(16.0)
    expect(identCuts(tape, listOf(boundary), words, emptyList())).toEqual(emptyList())
  }

  @Test
  fun `should keep no more than seven seconds of a long bed`() {
    val tape = taped(LOUD to 10.0..30.0)
    expect(identCuts(tape, listOf(ended(10.0)), spoken(25.0), emptyList()).map { it.seconds }).toEqual(listOf(7.0))
  }

  @Test
  fun `should reach back to where the jingle starts after a dip`() {
    val tape = taped(HUSH to 8.7..9.0, LOUD to 9.0..30.0)
    expect(identCuts(tape, listOf(ended(10.0)), spoken(14.0), emptyList()).map { it.start to it.seconds }).toEqual(listOf(9.0 to 4.8))
  }

  @Test
  fun `should skip a jingle the host talks over`() {
    val tape = taped(LOUD to 10.0..30.0)
    expect(identCuts(tape, listOf(ended(10.0)), spoken(12.0), emptyList())).toEqual(emptyList())
  }

  @Test
  fun `should skip a boundary no jingle was heard at`() {
    val tape = taped(LOUD to 10.0..30.0)
    expect(identCuts(tape, listOf(ended(10.0).copy(jingle = null)), spoken(20.0), emptyList())).toEqual(emptyList())
  }

  @Test
  fun `should keep the longest jingle of a programme`() {
    val tape = taped(LOUD to 10.0..30.0, LOUD to 40.0..60.0)
    expect(identCuts(tape, listOf(ended(10.0), ended(40.0)), spoken(15.0, 50.0), emptyList()).map { it.start }).toEqual(listOf(40.0))
  }

  @Test
  fun `should keep the better match of two jingles as long as each other`() {
    val tape = taped(LOUD to 10.0..30.0, LOUD to 40.0..60.0)
    val found = identCuts(tape, listOf(ended(10.0), ended(40.0, 0.97)), spoken(15.0, 45.0), emptyList())
    expect(found.map { it.r }).toEqual(listOf(0.97))
  }

  @Test
  fun `should keep a jingle for each programme of the recording`() {
    val tape = taped(LOUD to 10.0..30.0, LOUD to 40.0..60.0)
    val found = identCuts(tape, listOf(ended(10.0), ended(40.0).copy(programme = "Äripäeva õhtu")), spoken(15.0, 45.0), emptyList())
    expect(found.map { it.programme }).toEqual(listOf(SHOW, "Äripäeva õhtu"))
  }

  @Test
  fun `should keep the music after a break that says the programme's name`() {
    val tape = taped(LOUD to 10.0..30.0)
    val words = said(10.5, "Keskpäeva", "tund.") + spoken(16.0)
    expect(identCuts(tape, listOf(resumed(10.0)), words, music())).toEqual(listOf(IdentCut(SHOW, 10.0, 5.8, 0.9)))
  }

  @Test
  fun `should end a sting at its first quiet moment`() {
    val tape = taped(LOUD to 10.0..14.0, HUSH to 14.0..15.0)
    val words = said(10.5, "Keskpäevatund") + spoken(16.0)
    expect(identCuts(tape, listOf(resumed(10.0)), words, music()).map { it.seconds }).toEqual(listOf(4.0))
  }

  @Test
  fun `should count the words before the name as the sting's voice`() {
    val tape = taped(LOUD to 10.0..30.0)
    val words = said(10.5, "Kuku", "raadio", "KESKPÄEVATUND!") + spoken(15.0)
    expect(identCuts(tape, listOf(resumed(10.0)), words, music()).map { it.seconds }).toEqual(listOf(4.8))
  }

  @Test
  fun `should skip music after a break that does not say the programme's name`() {
    val tape = taped(LOUD to 10.0..30.0)
    expect(identCuts(tape, listOf(resumed(10.0)), spoken(11.0, 16.0), music())).toEqual(emptyList())
  }

  @Test
  fun `should skip a programme's name the host says without music`() {
    val tape = taped(LOUD to 10.0..30.0)
    val words = said(10.5, "Keskpäevatund") + spoken(16.0)
    expect(identCuts(tape, listOf(resumed(10.0)), words, music(0.42))).toEqual(emptyList())
  }

  @Test
  fun `should skip a sting the sound scores do not cover`() {
    val tape = taped(LOUD to 10.0..30.0)
    val words = said(10.5, "Keskpäevatund") + spoken(16.0)
    expect(identCuts(tape, listOf(resumed(10.0)), words, emptyList())).toEqual(emptyList())
  }

  @Test
  fun `should skip a name said later than four and a half seconds after the break`() {
    val tape = taped(LOUD to 10.0..30.0)
    val words = said(14.6, "Keskpäevatund") + spoken(18.0)
    expect(identCuts(tape, listOf(resumed(10.0)), words, music())).toEqual(emptyList())
  }

  @Test
  fun `should keep a sting of two and a half seconds`() {
    val tape = taped(LOUD to 10.0..30.0)
    val words = said(10.5, "Keskpäevatund") + spoken(12.7)
    expect(identCuts(tape, listOf(resumed(10.0)), words, music()).map { it.seconds }).toEqual(listOf(2.5))
  }

  @Test
  fun `should skip a sting shorter than two and a half seconds`() {
    val tape = taped(LOUD to 10.0..30.0)
    val words = said(10.5, "Keskpäevatund") + spoken(12.6)
    expect(identCuts(tape, listOf(resumed(10.0)), words, music())).toEqual(emptyList())
  }

  @Test
  fun `should prefer the station jingle to a longer sting`() {
    val tape = taped(LOUD to 10.0..30.0, LOUD to 40.0..60.0)
    val words = spoken(14.0) + said(40.5, "Keskpäevatund") + spoken(47.0)
    expect(identCuts(tape, listOf(ended(10.0), resumed(40.0)), words, music()).map { it.start }).toEqual(listOf(10.0))
  }

  @Test
  fun `should keep the end of the opening sting of a programme without a sting after its breaks`() {
    val tape = taped(LOUD to 10.0..20.0)
    val words = spoken(8.6) + said(15.0, "Keskpäeva", "tund.") + spoken(20.3)
    expect(identCuts(tape, listOf(opened(20.0)), words, music())).toEqual(listOf(IdentCut(SHOW, 12.1, 7.0, 0.9)))
  }

  @Test
  fun `should start the opening sting after the last word before the name`() {
    val tape = taped(LOUD to 10.0..20.0)
    val words = spoken(13.6) + said(15.0, "Keskpäevatund") + spoken(20.3)
    expect(identCuts(tape, listOf(opened(20.0)), words, music()).map { it.start to it.seconds }).toEqual(listOf(14.2 to 5.9))
  }

  @Test
  fun `should skip an opening whose last words are not the programme's name`() {
    val tape = taped(LOUD to 10.0..20.0)
    val words = said(15.0, "Keskpäevatund", "algab") + spoken(20.3)
    expect(identCuts(tape, listOf(opened(20.0)), words, music())).toEqual(emptyList())
  }

  @Test
  fun `should skip an opening whose name starts before the part that is kept`() {
    val tape = taped(LOUD to 5.0..20.0)
    val words = said(11.0, "Keskpäevatund") + spoken(20.3)
    expect(identCuts(tape, listOf(opened(20.0)), words, music())).toEqual(emptyList())
  }

  @Test
  fun `should skip an opening without music`() {
    val tape = taped(LOUD to 10.0..20.0)
    val words = said(15.0, "Keskpäevatund") + spoken(20.3)
    expect(identCuts(tape, listOf(opened(20.0)), words, music(0.3))).toEqual(emptyList())
  }

  @Test
  fun `should prefer the sting after a break to the opening sting`() {
    val tape = taped(LOUD to 10.0..20.0, LOUD to 40.0..60.0)
    val words = said(15.0, "Keskpäevatund") + spoken(20.3) + said(40.5, "Keskpäevatund") + spoken(44.0)
    expect(identCuts(tape, listOf(opened(20.0), resumed(40.0)), words, music()).map { it.start }).toEqual(listOf(40.0))
  }

  @Test
  fun `should list the saved jingles whose audio is still there`() {
    val kept = Ident(Station.KUKU, "Keskpäevatund", "kuku-keskpaevatund.m4a", 5.2)
    Files.write(directory.resolve(kept.file), byteArrayOf(1))
    save(directory.resolve(IDENTS_FILE), listOf(kept, kept.copy(station = Station.ARIPAEV, file = "aripaev-keskpaevatund.m4a")))
    expect(idents(directory)).toEqual(listOf(kept))
  }

  @Test
  fun `should list no jingles before any was saved`() {
    expect(idents(directory)).toEqual(emptyList())
  }

  @Test
  fun `should list no jingles when the saved list cannot be read`() {
    Files.writeString(directory.resolve(IDENTS_FILE), "{ katki õ")
    expect(idents(directory)).toEqual(emptyList())
  }

  @Test
  fun `should join two stories with a pause when the next has no jingle`() {
    val pause = directory.resolve("pause.m4a")
    expect(join(180.0, 181.0, pause, null)).toEqual(listOf(Span(180.0, 181.0, pause, 180.0, pause)))
  }

  @Test
  fun `should play the jingle between a short lead and a shorter tail of silence`() {
    val pause = directory.resolve("pause.m4a")
    val ident = directory.resolve("kuku-keskpaevatund.m4a")
    val lead = Span(180.0, 180.35, pause, 180.0, pause)
    val tail = Span(185.45, 185.5, pause, 185.45, pause)
    expect(join(180.0, 185.5, pause, ident)).toEqual(listOf(lead, Span(180.35, 185.45, ident, 180.35, ident), tail))
  }

  private fun taped(vararg levels: Pair<Int, ClosedFloatingPointRange<Double>>): Timeline {
    val samples = constant(70.0, TALK)
    levels.forEach { (level, span) -> samples.fill(level.toShort(), sample(span.start).toInt(), sample(span.endInclusive).toInt()) }
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, samples)))
    return timeline(directory)
  }

  private fun ended(
    seconds: Double,
    r: Double = 0.9,
  ) = Boundary("break 1 ends", seconds, "station jingle, r=$r", false, JingleMatch(seconds, r), SHOW)

  private fun spoken(vararg starts: Double) = starts.map { TranscriptWord(it, it + 0.4, "õhtust") }

  private fun resumed(seconds: Double) = Boundary("break 1 ends", seconds, "level dip", false, null, SHOW)

  private fun opened(seconds: Double) = Boundary("show starts", seconds, "transcript only", false, null, SHOW)

  private fun said(
    start: Double,
    vararg words: String,
  ) = words.mapIndexed { index, word -> TranscriptWord(start + index * 0.5, start + index * 0.5 + 0.4, word) }

  private fun music(score: Double = 0.9) = heard(0.0, 70.0, 0.1, score)

  companion object {
    private const val SHOW = "Keskpäevatund"
    private const val TALK = 4000
    private const val LOUD = 8000
    private const val HUSH = 50
  }
}
