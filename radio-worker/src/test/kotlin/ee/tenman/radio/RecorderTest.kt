package ee.tenman.radio

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualWithErrorTolerance
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import com.fasterxml.jackson.annotation.JsonProperty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class RecorderTest {
  @Test
  fun `copies the stream without reencoding`(
    @TempDir tmp: Path,
  ) {
    val directory = recorded(tmp)
    expect(read<Probe>(directory.resolve("raw.probe.json")).streams.first()).toEqual(Stream("mp3", "48000", 2, "256000"))
  }

  @Test
  fun `records the requested length`(
    @TempDir tmp: Path,
  ) {
    expect(status(recorded(tmp)).parts.first().durationSeconds!!).toEqualWithErrorTolerance(12.0, 0.1)
  }

  @Test
  fun `writes 16 khz mono pcm next to the mp3`(
    @TempDir tmp: Path,
  ) {
    expect(seconds(recorded(tmp).resolve("raw.pcm"))).toEqualWithErrorTolerance(12.0, 0.05)
  }

  @Test
  fun `stores the sha256 of each part`(
    @TempDir tmp: Path,
  ) {
    val directory = recorded(tmp)
    expect(status(directory).parts.first().sha256).toEqual(sha256(directory.resolve("raw.mp3")))
  }

  @Test
  fun `marks a complete recording captured`(
    @TempDir tmp: Path,
  ) {
    expect(status(recorded(tmp)).state).toEqual(RecordingState.CAPTURED)
  }

  @Test
  fun `counts nothing lost when the parts overlap`() {
    val parts =
      listOf(
        Part("raw.mp3", 1.4, 0.0, 10.0),
        Part("raw-part-002.mp3", 10.9, 9.5, 10.0),
        Part("raw-part-003.mp3", 20.4, 19.0, 11.0),
      )
    expect(lost(30.0, parts)).toEqual(0L)
  }

  @Test
  fun `counts the seconds missing between two parts`() {
    expect(lost(30.0, listOf(Part("raw-part-002.mp3", 15.4, 14.0, 16.0), Part("raw.mp3", 1.4, 0.0, 10.0)))).toEqual(4L)
  }

  @Test
  fun `counts the seconds missing after the last part`() {
    expect(lost(30.0, listOf(Part("raw.mp3", 1.4, 0.0, 21.0)))).toEqual(9L)
  }

  @Test
  fun `marks a recording in parts with nothing lost captured`() {
    val parts = listOf(Part("raw.mp3", 1.4, 0.0, 10.0), Part("raw-part-002.mp3", 11.4, 10.0, 20.0))
    expect(verdict(parts, 0)).toEqual(RecordingState.CAPTURED)
  }

  @Test
  fun `logs when each part starts and ends`(
    @TempDir tmp: Path,
  ) {
    val lines = Files.readAllLines(recorded(tmp).resolve("events.log"))
    expect(lines.map { it.split(" ").subList(1, 3) }).toEqual(listOf(listOf("raw.mp3", "started"), listOf("raw.mp3", "ended")))
  }

  @Test
  fun `carries on into a new part after a restart`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("raadio")
    val before =
      replay(tmp).use { station ->
        prepare(directory, station.url, 4.0)
        Files.copy(tone(tmp.resolve("t.mp3"), 3.0), directory.resolve("raw.mp3"))
        silence(directory.resolve("raw.pcm"), 3.0)
        write(directory.resolve("status.json"), status(directory).copy(parts = listOf(Part("raw.mp3", 1.4, 0.0))))
        sha256(directory.resolve("raw.mp3")).also { record(directory, CountDownLatch(1)) }
      }
    val after = Pair(status(directory).parts.map { it.file }, sha256(directory.resolve("raw.mp3")))
    expect(after).toEqual(Pair(listOf("raw.mp3", "raw-part-002.mp3"), before))
  }

  @Test
  fun `places a part after a drop at its place in the stream`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("raadio")
    replay(tmp, 60.0).use { station ->
      prepare(directory, station.url, 40.0)
      val recorder = thread { record(directory, CountDownLatch(1)) }
      waitFor { caughtUp(directory, tmp.resolve("hls/stream.m3u8")) }
      recorders(directory.resolve("raw.mp3")).forEach { it.destroyForcibly() }
      recorder.join(40_000)
    }
    val second = status(directory).parts[1]
    expect((second.offsetSeconds + firstSilence(directory.resolve("raw-part-002.pcm"), 0.5)) % 10).toEqualWithErrorTolerance(7.0, 0.1)
  }

  @Test
  fun `starts a part after a long stop where the last one ended`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("raadio")
    replay(tmp, 60.0).use { station ->
      prepare(directory, station.url, 50.0)
      val first = CountDownLatch(1)
      val recorder = thread { record(directory, first) }
      waitFor { caughtUp(directory, tmp.resolve("hls/stream.m3u8")) }
      first.countDown()
      recorder.join(30_000)
      Files.setLastModifiedTime(directory.resolve("raw.mp3"), FileTime.from(Instant.now().minusSeconds(600)))
      val second = CountDownLatch(1)
      val resumed = thread { record(directory, second) }
      waitFor { status(directory).parts.size == 2 }
      second.countDown()
      resumed.join(30_000)
    }
    expect(status(directory).parts[1].offsetSeconds).toBeLessThanOrEqualTo(0.0)
  }

  @Test
  fun `starts a late recording at its start`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("raadio")
    replay(tmp, 60.0).use { station ->
      get(station.url)
      waitFor { Files.readAllLines(tmp.resolve("hls/stream.m3u8")).count { it.endsWith(".ts") } >= 8 }
      prepare(directory, station.url, 30.0, started = now() - 24.0)
      record(directory, CountDownLatch(1))
    }
    expect(status(directory).state).toEqual(RecordingState.CAPTURED)
  }

  @Test
  fun `reaches back over the seconds the worker was away`() {
    expect(listOf(null, 0.4, 19.0, 86400.0).map { reach(KUKU, it) }).toEqual(listOf(0, 0, 3, 600))
  }

  @Test
  fun `reaches back only in a live playlist`() {
    expect(reach("/recordings/keskpäevatund.mp3", 19.0)).toEqual(0)
  }

  @Test
  fun `asks ffmpeg for the missed segments and one more`() {
    expect(command(KUKU, 30.0, 4, Path.of("raw-part-002")).joinToString(" "))
      .toContain("-rw_timeout 15000000 -live_start_index -7 -t 60.000 -i $KUKU")
  }

  @Test
  fun `starts a new part at the live edge`() {
    expect(command(KUKU, 30.0, 0, Path.of("raw")).joinToString(" ")).toContain("-rw_timeout 15000000 -t 30.000 -i $KUKU")
  }

  @Test
  fun `places parts across the timestamp wrap`(
    @TempDir tmp: Path,
  ) {
    expect(offsets(tmp, "95413.694667", "5.976978")).toEqual(listOf(0.0, 36.0))
  }

  @Test
  fun `places a part that starts before the first one`(
    @TempDir tmp: Path,
  ) {
    expect(offsets(tmp, "100.000000", "94.000000")).toEqual(listOf(0.0, -6.0))
  }

  @Test
  fun `adopts a part it never registered`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("raadio")
    prepare(directory, KUKU, 0.0)
    Files.createFile(directory.resolve("raw.mp3"))
    Files.writeString(directory.resolve("raw.log"), "  Duration: N/A, start: 1.400000, bitrate: N/A\n")
    record(directory, CountDownLatch(1))
    expect(status(directory).parts.map { it.file }).toEqual(listOf("raw.mp3"))
  }

  @Test
  fun `names the programmes it records in the status`(
    @TempDir tmp: Path,
  ) {
    prepare(tmp.resolve("raadio"), KUKU, 0.0, listOf("Keskpäevatund", "Tarkade klubi"))
    expect(status(tmp.resolve("raadio")).programmes).toEqual(listOf("Keskpäevatund", "Tarkade klubi"))
  }

  @Test
  fun `keeps the air slot of its programmes in the status`(
    @TempDir tmp: Path,
  ) {
    prepare(tmp.resolve("raadio"), KUKU, 0.0, slot = Slot(1792832400, 1792836000))
    expect(status(tmp.resolve("raadio")).slot).toEqual(Slot(1792832400, 1792836000))
  }

  @Test
  fun `refuses a directory another worker holds`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("raadio")
    prepare(directory, KUKU, 0.0)
    hold(directory).use { expect { hold(directory) }.toThrow<RecordingRefusedException>().messageToContain("another worker") }
  }

  @Test
  fun `refuses a directory that is not its own`(
    @TempDir tmp: Path,
  ) {
    Files.writeString(tmp.resolve("status.json"), """{"state": "captured_needs_review"}""")
    expect { prepare(tmp, KUKU, 3.0) }.toThrow<RecordingRefusedException>()
  }

  @Test
  fun `ends at its end time when the source never answers`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("raadio")
    prepare(directory, "http://127.0.0.1:9/stream.m3u8", 3.0)
    val recorder = thread { record(directory, CountDownLatch(1)) }
    recorder.join(10_000)
    val status = status(directory)
    expect(Triple(recorder.isAlive, status.state, status.parts)).toEqual(Triple(false, RecordingState.FAILED, emptyList()))
  }

  private fun recorded(tmp: Path): Path =
    replay(tmp).use { station ->
      tmp.resolve("keskpäevatund").also {
        prepare(it, station.url, 12.0)
        record(it, CountDownLatch(1))
      }
    }

  private fun offsets(
    tmp: Path,
    vararg starts: String,
  ): List<Double> {
    val directory = tmp.resolve("raadio").also { prepare(it, KUKU, 0.0) }
    val stems = listOf("raw") + (2..starts.size).map { "raw-part-%03d".format(it) }
    stems.zip(starts).forEach { (stem, start) ->
      Files.createFile(directory.resolve("$stem.mp3"))
      Files.writeString(directory.resolve("$stem.log"), "  Duration: N/A, start: $start, bitrate: N/A\n")
    }
    return stems.fold(status(directory)) { status, stem -> register(directory, status, stem) }.parts.map { it.offsetSeconds }
  }

  private data class Stream(
    @JsonProperty("codec_name") val codec: String,
    @JsonProperty("sample_rate") val rate: String,
    val channels: Int,
    @JsonProperty("bit_rate") val bits: String,
  )

  private data class Probe(
    val streams: List<Stream>,
  )
}
