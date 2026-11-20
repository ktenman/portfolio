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
    expect(identCuts(tape, listOf(ended(10.0)), spoken(16.0))).toEqual(listOf(IdentCut(SHOW, 10.0, 5.0, 0.9)))
  }

  @Test
  fun `should end the ident before the host's first word when the bed plays on`() {
    val tape = taped(LOUD to 10.0..30.0)
    expect(identCuts(tape, listOf(ended(10.0)), spoken(15.0)).map { it.seconds }).toEqual(listOf(4.8))
  }

  @Test
  fun `should count the first word from the cut when the jingle is cut out`() {
    val tape = taped(LOUD to 10.0..30.0)
    val boundary = ended(10.0).copy(seconds = 14.0)
    expect(identCuts(tape, listOf(boundary), spoken(11.0, 16.0)).map { it.seconds }).toEqual(listOf(5.8))
  }

  @Test
  fun `should keep no more than seven seconds of a long bed`() {
    val tape = taped(LOUD to 10.0..30.0)
    expect(identCuts(tape, listOf(ended(10.0)), spoken(25.0)).map { it.seconds }).toEqual(listOf(7.0))
  }

  @Test
  fun `should reach back to where the jingle starts after a dip`() {
    val tape = taped(HUSH to 8.7..9.0, LOUD to 9.0..30.0)
    expect(identCuts(tape, listOf(ended(10.0)), spoken(14.0)).map { it.start to it.seconds }).toEqual(listOf(9.0 to 4.8))
  }

  @Test
  fun `should skip a jingle the host talks over`() {
    val tape = taped(LOUD to 10.0..30.0)
    expect(identCuts(tape, listOf(ended(10.0)), spoken(12.0))).toEqual(emptyList())
  }

  @Test
  fun `should skip a boundary no jingle was heard at`() {
    val tape = taped(LOUD to 10.0..30.0)
    expect(identCuts(tape, listOf(ended(10.0).copy(jingle = null)), spoken(20.0))).toEqual(emptyList())
  }

  @Test
  fun `should keep the longest jingle of a programme`() {
    val tape = taped(LOUD to 10.0..30.0, LOUD to 40.0..60.0)
    expect(identCuts(tape, listOf(ended(10.0), ended(40.0)), spoken(15.0, 50.0)).map { it.start }).toEqual(listOf(40.0))
  }

  @Test
  fun `should keep the better match of two jingles as long as each other`() {
    val tape = taped(LOUD to 10.0..30.0, LOUD to 40.0..60.0)
    val found = identCuts(tape, listOf(ended(10.0), ended(40.0, 0.97)), spoken(15.0, 45.0))
    expect(found.map { it.r }).toEqual(listOf(0.97))
  }

  @Test
  fun `should keep a jingle for each programme of the recording`() {
    val tape = taped(LOUD to 10.0..30.0, LOUD to 40.0..60.0)
    val found = identCuts(tape, listOf(ended(10.0), ended(40.0).copy(programme = "Äripäeva õhtu")), spoken(15.0, 45.0))
    expect(found.map { it.programme }).toEqual(listOf(SHOW, "Äripäeva õhtu"))
  }

  @Test
  fun `should list the saved jingles whose audio is still there`() {
    val kept = Ident(Station.KUKU, "Keskpäevatund", "kuku-keskpaevatund.m4a", 5.2, "2026-11-14T10:00:00Z")
    Files.write(directory.resolve(kept.file), byteArrayOf(1))
    save(directory.resolve(IDENTS_FILE), listOf(kept, kept.copy(station = Station.ARIPAEV, file = "aripaev-keskpaevatund.m4a")))
    expect(idents(directory)).toEqual(listOf(kept))
  }

  @Test
  fun `should list no jingles before any was saved`() {
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

  companion object {
    private const val SHOW = "Keskpäevatund"
    private const val TALK = 4000
    private const val LOUD = 8000
    private const val HUSH = 50
  }
}
