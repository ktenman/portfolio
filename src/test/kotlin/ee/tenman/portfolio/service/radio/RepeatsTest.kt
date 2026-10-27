package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

class RepeatsTest {
  @TempDir
  lateinit var root: Path

  private fun hash(print: Long) = (print ushr 32).toInt()

  private fun frame(print: Long) = print.toInt()

  private fun files(
    directory: Path,
    parts: List<Pair<Double, ShortArray>>,
    mp3: Boolean,
  ): List<FixturePart> =
    parts.mapIndexed { index, (offset, samples) ->
      FixturePart("raw-$index.mp3", offset, if (mp3) encoded(directory, "raw-$index", samples) else samples)
    }

  private fun earlier(
    vararg parts: Pair<Double, ShortArray>,
    name: String = "2026-10-25-1200-maaelu",
  ): Fingerprinted {
    val directory = Files.createDirectories(root.resolve(name))
    writeRecording(directory, files(directory, parts.toList(), mp3 = true))
    return Fingerprinted(directory, readStatus(directory).parts, fingerprint(timeline(directory)))
  }

  private fun matched(
    vararg parts: Pair<Double, ShortArray>,
    others: List<Fingerprinted> = emptyList(),
  ): Repeats {
    val directory = Files.createDirectories(root.resolve("2026-10-26-1100-digitund"))
    writeRecording(directory, files(directory, parts.toList(), mp3 = false))
    val recorded = timeline(directory)
    return repeats(recorded, directory.fileName.toString(), fingerprint(recorded), others)
  }

  @Test
  fun `should match a jingle across a ten minute block seam at its true offset`() {
    writeRecording(root, listOf(FixturePart("raw.mp3", 0.0, noise(640.0).aired(20.0).aired(598.4))))
    val prints = fingerprint(timeline(root))
    val first = prints.filter { frame(it) in 1250 until 1500 }.groupBy(::hash, ::frame)
    val offsets =
      prints.filter { frame(it) in 37500 until 37650 }.flatMap { print ->
        first[hash(print)].orEmpty().map { frame(print) - it }
      }
    expect(
      offsets
        .groupingBy { it }
        .eachCount()
        .maxBy { it.value }
        .key,
    ).toEqual(36150)
  }

  @Test
  fun `should ignore fingerprints of an older version`() {
    Files.write(
      root.resolve(FINGERPRINTS),
      ByteBuffer
        .allocate(12)
        .putInt(0)
        .putLong(42)
        .array(),
    )
    expect(readFingerprints(root)).toEqual(null)
  }

  @Test
  fun `should read back the fingerprints it wrote`() {
    writeFingerprints(root, longArrayOf(3, 30064771077))
    expect(readFingerprints(root)?.toList()).toEqual(listOf(3L, 30064771077L))
  }

  @Test
  fun `should find no fingerprints in a recording without a whole frame`() {
    writeRecording(root, listOf(FixturePart("raw.mp3", 0.0, ShortArray(0))))
    expect(fingerprint(timeline(root)).size).toEqual(0)
  }

  @Test
  fun `should find a jingle that airs twice in one recording to within fifty milliseconds`() {
    val start = matched(0.0 to noise(60.0).aired(20.0).aired(50.0)).start(49.0, 52.0)
    expect(start?.let { abs(it - 50.0) <= 0.05 }).toEqual(true)
  }

  @Test
  fun `should find a jingle in a recording from the fingerprints of an earlier one`() {
    val earlier = earlier(0.0 to noise(60.0, seed = 1).aired(20.0))
    val start = matched(0.0 to noise(60.0, seed = 2).aired(30.0), others = listOf(earlier)).start(29.0, 32.0)
    expect(start?.let { abs(it - 30.0) <= 0.05 }).toEqual(true)
  }

  @Test
  fun `should find a jingle in a recording from the fingerprints of a later one`() {
    val later = earlier(0.0 to noise(60.0, seed = 1).aired(20.0), name = "2026-10-27-1200-maaelu")
    val start = matched(0.0 to noise(60.0, seed = 2).aired(30.0), others = listOf(later)).start(29.0, 32.0)
    expect(start?.let { abs(it - 30.0) <= 0.05 }).toEqual(true)
  }

  @Test
  fun `should find a jingle in a later part of an earlier recording`() {
    val earlier = earlier(0.0 to noise(40.0, seed = 1), 50.0 to noise(30.0, seed = 3).aired(10.0))
    val start = matched(0.0 to noise(60.0, seed = 2).aired(30.0), others = listOf(earlier)).start(29.0, 32.0)
    expect(start?.let { abs(it - 30.0) <= 0.05 }).toEqual(true)
  }

  @Test
  fun `should find a jingle just after the seam of two adjacent parts of an earlier recording`() {
    val earlier = earlier(0.0 to noise(20.0, seed = 1), 20.0 to noise(40.0, seed = 3).aired(1.2))
    val start = matched(0.0 to noise(60.0, seed = 2).aired(30.0), others = listOf(earlier)).start(29.0, 32.0)
    expect(start?.let { abs(it - 30.0) <= 0.05 }).toEqual(true)
  }

  @Test
  fun `should find nothing repeated in two recordings of different noise`() {
    val earlier = earlier(0.0 to noise(60.0, seed = 1))
    expect(matched(0.0 to noise(60.0, seed = 2), others = listOf(earlier)).start(0.0, 60.0)).toEqual(null)
  }

  @Test
  fun `should reject the same music under a different voice as loud as the music`() {
    val samples = noise(60.0).aired(20.0, mixed(melody(), noise(4.0, seed = 5))).aired(50.0, mixed(melody(), noise(4.0, seed = 6)))
    expect(matched(0.0 to samples).start(49.0, 55.0)).toEqual(null)
  }

  @Test
  fun `should not extend a repeat into audio one airing is missing`() {
    val first = noise(40.0).also { it.fill(0, 19 * RATE, 20 * RATE) }.aired(20.0)
    val start = matched(0.0 to first, 50.0 to noise(10.0, seed = 5).aired(0.0)).start(18.0, 25.0)
    expect(start?.let { abs(it - 20.0) <= 0.05 }).toEqual(true)
  }

  @Test
  fun `should skip a recording whose audio is gone`() {
    val earlier = earlier(0.0 to noise(60.0, seed = 1).aired(20.0))
    Files.delete(earlier.directory.resolve("raw-0.mp3"))
    expect(matched(0.0 to noise(60.0, seed = 2).aired(30.0), others = listOf(earlier)).start(29.0, 32.0)).toEqual(null)
  }

  @Test
  fun `should find a jingle that airs twice beside an earlier recording whose audio cannot be decoded`() {
    val earlier = earlier(0.0 to noise(60.0, seed = 1).aired(20.0))
    Files.writeString(earlier.directory.resolve("raw-0.mp3"), "katkine")
    val start = matched(0.0 to noise(60.0, seed = 2).aired(30.0).aired(50.0), others = listOf(earlier)).start(49.0, 52.0)
    expect(start?.let { abs(it - 50.0) <= 0.05 }).toEqual(true)
  }

  @Test
  fun `should match nothing in a recording whose fingerprints pair up past the ceiling`() {
    writeRecording(root, listOf(FixturePart("raw.mp3", 0.0, noise(60.0).aired(20.0).aired(50.0))))
    val recorded = timeline(root)
    val prints = (fingerprint(recorded) + LongArray(7000) { (1L shl 32) or (100_000L + it) }).sortedArray()
    expect(repeats(recorded, root.fileName.toString(), prints, emptyList()).start(49.0, 52.0)).toEqual(null)
  }

  @Test
  @Timeout(10)
  fun `should stop counting fingerprint pairs once they pass the ceiling`() {
    val prints = LongArray(300_000) { (1L shl 32) or it.toLong() }
    expect(repeats(Timeline(root, emptyList()), root.fileName.toString(), prints, emptyList()).start(0.0, 60.0)).toEqual(null)
  }

  @Test
  fun `should not start a repeat that was already on when the last word started`() {
    expect(matched(0.0 to noise(60.0).aired(20.0).aired(49.504)).start(50.0, 51.2)).toEqual(null)
  }
}
