package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class RadioCutServiceTest {
  @TempDir
  lateinit var directory: Path

  private val service = RadioCutService()

  private fun recording(samples: ShortArray): Pair<Timeline, DoubleArray> {
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, samples)))
    val recorded = timeline(directory)
    return recorded to loudness(recorded)
  }

  private fun chirp(seconds: Double): ShortArray =
    ShortArray((seconds * RATE).toInt()) {
      val t = it.toDouble() / RATE
      (8000 * sin(2 * PI * (300 * t + 400 * t * t))).toInt().toShort()
    }

  private fun withJingles(
    samples: ShortArray,
    returns: List<Double>,
  ): ShortArray {
    returns.forEach { back ->
      samples.fill(0, ((back - 3.0) * RATE).toInt(), ((back - 2.7) * RATE).toInt())
      chirp(1.2).copyInto(samples, ((back - 2.7) * RATE).toInt())
    }
    return samples
  }

  private fun tone(hertz: Int): ShortArray = ShortArray(RATE) { (8000 * sin(2 * PI * hertz * it / RATE)).toInt().toShort() }

  private fun saved(
    name: String,
    samples: ShortArray,
  ) = SavedJingle(name, DoubleArray(RATE) { samples[it].toDouble() })

  private fun line(start: Double) =
    TranscriptLine(start, start + 1, "Ja siinkohal väike paus.", listOf(TranscriptWord(start, start + 1, " paus.")))

  private fun outline(
    before: Double,
    first: Double,
    breaks: List<Pair<Double, Double>>,
    end: Double,
    music: Boolean,
  ) = Consensus(emptyList(), music, before, first, breaks.map { Pause(it.first, it.second) }, end, emptyList(), 1)

  @Test
  fun `should start a break in the middle of the first silence`() {
    val (_, db) = recording(noise(20.0, listOf(12.0)))
    val (seconds, method) = leave(db, 10.0, 6.0)
    expect((abs(seconds - 12.15) <= 0.011) to method).toEqual(true to "digital silence")
  }

  @Test
  fun `should start a break at a level dip when there is no silence`() {
    val samples = noise(20.0)
    (12 * RATE).until((12.2 * RATE).toInt()).forEach { samples[it] = (samples[it] / 100).toShort() }
    val (_, db) = recording(samples)
    val (seconds, method) = leave(db, 10.0, 6.0)
    expect((abs(seconds - 12.1) <= 0.1) to method).toEqual(true to "level dip")
  }

  @Test
  fun `should snap an end picked by hand to the nearest silence`() {
    val (_, db) = recording(noise(20.0, listOf(8.0, 12.0)))
    expect(abs(snap(db, 11.0) - 12.15) <= 0.011).toEqual(true)
  }

  @Test
  fun `should keep an end picked by hand where the audio gives nothing nearby`() {
    val (_, db) = recording(noise(20.0, listOf(4.0)))
    expect(snap(db, 10.0)).toEqual(10.0)
  }

  @Test
  fun `should start a break from the transcript when the audio gives nothing`() {
    val (_, db) = recording(noise(20.0))
    expect(leave(db, 10.0, 6.0)).toEqual(10.2 to "transcript only")
  }

  @Test
  fun `should end both breaks before the jingle they share`() {
    val (recorded, db) = recording(withJingles(noise(60.0), listOf(20.0, 45.0)))
    val shared = checkNotNull(jingle(recorded, listOf(20.0, 45.0)))
    expect(listOf(20.0, 45.0).zip(shared).all { (back, match) -> abs(back - (match?.start ?: 0.0) - 2.75) <= 0.06 }).toEqual(true)
  }

  private fun withShared(
    seconds: Double,
    returns: List<Double>,
  ): ShortArray {
    val samples = noise(120.0)
    returns.forEach { back -> chirp(seconds).copyInto(samples, ((back - seconds) * RATE).toInt()) }
    return samples
  }

  @Test
  fun `should not take an ad that closes both breaks for their jingle`() {
    val (recorded, _) = recording(withShared(30.0, listOf(50.0, 100.0)))
    expect(jingle(recorded, listOf(50.0, 100.0))).toEqual(null)
  }

  @Test
  fun `should keep a shared stretch as long as a jingle`() {
    val (recorded, _) = recording(withShared(3.0, listOf(50.0, 100.0)))
    val shared = checkNotNull(jingle(recorded, listOf(50.0, 100.0)))
    expect(shared.map { abs((it?.start ?: 0.0) % 50.0 - 47.0) <= 0.06 }).toEqual(listOf(true, true))
  }

  @Test
  fun `should end a break at the last dip without a shared jingle`() {
    val samples = noise(60.0)
    (38 * RATE).until((38.2 * RATE).toInt()).forEach { samples[it] = (samples[it] / 100).toShort() }
    val (recorded, db) = recording(samples)
    val found =
      service.boundaries(
        recorded,
        db,
        outline(1.0, 5.0, listOf(30.0 to 40.0), 50.0, false),
        listOf(1.0, 5.0, 30.0, 40.0, 50.0).map(::line),
        emptyList(),
      )
    expect(found[2].method to (abs(found[2].seconds - 38.1) <= 0.1)).toEqual("level dip" to true)
  }

  private fun paired(library: List<SavedJingle>): List<Boundary> {
    val (recorded, db) = recording(withJingles(noise(60.0), listOf(20.0, 45.0)))
    val lines = listOf(1.0, 5.0, 8.0, 20.0, 30.0, 45.0, 50.0).map(::line)
    return service.boundaries(recorded, db, outline(1.0, 5.0, listOf(8.0 to 20.0, 30.0 to 45.0), 50.0, false), lines, library)
  }

  @Test
  fun `should end breaks that share a jingle at it`() {
    val found = paired(emptyList())
    expect(found.map { it.method.startsWith("station jingle") }).toEqual(listOf(false, false, true, false, true, false))
  }

  @Test
  fun `should start a show without music just before the first word`() {
    val (recorded, db) = recording(noise(60.0))
    val lines = listOf(1.0, 5.0, 50.0).map(::line)
    val found = service.boundaries(recorded, db, outline(1.0, 5.0, emptyList(), 50.0, false), lines, emptyList())
    expect(found.first()).toEqual(Boundary("show starts", 4.7, "transcript only", true))
  }

  @Test
  fun `should start a show with music in the silence before the hosts`() {
    val (recorded, db) = recording(noise(60.0, listOf(3.0)))
    val lines = listOf(1.0, 5.0, 50.0).map(::line)
    val found = service.boundaries(recorded, db, outline(1.0, 5.0, emptyList(), 50.0, true), lines, emptyList())
    expect((abs(found.first().seconds - 3.15) <= 0.011) to found.first().review).toEqual(true to false)
  }

  @Test
  fun `should refuse a break that ends before it starts`() {
    val (recorded, db) = recording(noise(60.0))
    val lines = listOf(1.0, 5.0, 20.0, 30.0, 50.0).map(::line)
    expect { service.boundaries(recorded, db, outline(1.0, 5.0, listOf(30.0 to 20.0), 50.0, false), lines, emptyList()) }
      .toThrow<IllegalStateException>()
      .messageToContain("break 1 ends")
  }

  @Test
  fun `should skip a jingle candidate whose template is digital silence`() {
    val samples = noise(60.0)
    samples.fill(0, 10 * RATE, (11.3 * RATE).toInt())
    samples.copyInto(samples, (34.7 * RATE).toInt(), (9.7 * RATE).toInt(), 10 * RATE)
    val (recorded, _) = recording(samples)
    expect(jingle(recorded, listOf(20.0, 45.0))).toEqual(null)
  }

  private fun single(library: List<SavedJingle>): List<Boundary> {
    val (recorded, db) = recording(withJingles(noise(60.0), listOf(45.0)))
    val lines = listOf(1.0, 5.0, 30.0, 45.0, 50.0).map(::line)
    return service.boundaries(recorded, db, outline(1.0, 5.0, listOf(30.0 to 45.0), 50.0, false), lines, library)
  }

  @Test
  fun `should end a single break in the silence before a saved jingle`() {
    val found = single(listOf(saved("2026-10-17-1", chirp(1.0))))
    expect(found[2].method to (abs(found[2].seconds - 42.0) <= 0.02)).toEqual("station jingle, r=1.00" to true)
  }

  @Test
  fun `should record the saved jingle and its correlation on the break end`() {
    expect(single(listOf(saved("2026-10-17-1", chirp(1.0))))[2].jingle).toEqual(JingleMatch(42.3, 1.0, "2026-10-17-1"))
  }

  @Test
  fun `should pick the saved jingle that matches best`() {
    val found = single(listOf(saved("2026-10-10-1", tone(2000)), saved("2026-10-17-1", chirp(1.0))))
    expect(found[2].jingle?.saved).toEqual("2026-10-17-1")
  }

  @Test
  fun `should end a single break with an unknown jingle at the last dip`() {
    expect(single(listOf(saved("2026-10-10-1", tone(2000))))[2].method).toEqual("level dip")
  }

  @Test
  fun `should end breaks at a saved jingle before comparing them`() {
    val found = paired(listOf(saved("2026-10-17-1", chirp(1.0))))
    expect(listOf(found[2], found[4]).map { it.jingle?.saved }).toEqual(listOf("2026-10-17-1", "2026-10-17-1"))
  }

  @Test
  fun `should compare the breaks when no saved jingle matches them`() {
    val found = paired(listOf(saved("2026-10-10-1", tone(2000))))
    expect(listOf(found[2], found[4]).map { it.method.substringBefore(",") to it.jingle?.saved })
      .toEqual(listOf("station jingle" to null, "station jingle" to null))
  }
}
