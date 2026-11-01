package ee.tenman.radio

import ch.tutteli.atrium.api.fluent.en_GB.cause
import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.notToContain
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualWithErrorTolerance
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import com.fasterxml.jackson.annotation.JsonProperty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tools.jackson.core.JacksonException
import java.lang.ProcessBuilder.Redirect
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs

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
    expect(status(adopted(tmp)).parts.first().sha256).toEqual("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
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
        Files.copy(tone(tmp.resolve("t.mp3"), 20.0), directory.resolve("raw.mp3"))
        silence(directory.resolve("raw.pcm"), 20.0)
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
    val (first, second) = status(directory).parts
    val placed = (second.offsetSeconds + firstSilence(directory.resolve("raw-part-002.pcm"), 0.5)) % 10
    expect(Pair(second.offsetSeconds < first.durationSeconds!!, abs(placed - 7.0) <= 0.1)).toEqual(Pair(true, true))
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
  fun `starts a part after the window ended when a stop left audio missing`(
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
      write(directory.resolve("status.json"), status(directory).copy(endsAt = now() - 3))
      Files.setLastModifiedTime(directory.resolve("raw.mp3"), FileTime.from(Instant.now().minusSeconds(12)))
      record(directory, CountDownLatch(1))
    }
    expect(status(directory).parts.map { it.file }).toEqual(listOf("raw.mp3", "raw-part-002.mp3"))
  }

  @Test
  fun `starts no part after the window ended when only a hole is missing`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("raadio")
    val parts = listOf(Part("raw.mp3", 1.4, 0.0, 2.0), Part("raw-part-002.mp3", 4.4, 3.0, 1.0))
    replay(tmp).use { station ->
      prepare(directory, station.url, 4.0, started = now() - 10)
      parts.forEach { Files.createFile(directory.resolve(it.file)) }
      write(directory.resolve("status.json"), status(directory).copy(parts = parts))
      record(directory, CountDownLatch(1))
    }
    expect(status(directory).parts).toEqual(parts)
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
  fun `records the whole length when the first part starts late`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("raadio")
    replay(tmp).use { station ->
      prepare(directory, station.url, 12.0, started = now() - 2.0)
      record(directory, CountDownLatch(1))
    }
    expect(status(directory).state).toEqual(RecordingState.CAPTURED)
  }

  @Test
  fun `reads a file to its end when the recording starts late`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("keskpäevatund")
    prepare(directory, "${tone(tmp.resolve("in.mp3"), 12.0)}", 12.0)
    write(directory.resolve("status.json"), status(directory).copy(endsAt = now() + 9))
    record(directory, CountDownLatch(1))
    expect(status(directory).state).toEqual(RecordingState.CAPTURED)
  }

  @Test
  fun `ends a late live recording at its start plus its seconds`(
    @TempDir tmp: Path,
  ) {
    prepare(tmp.resolve("raadio"), KUKU, 30.0, started = 1792832400.0)
    expect(status(tmp.resolve("raadio")).endsAt).toEqual(1792832430.0)
  }

  @Test
  fun `reaches back over the seconds the worker was away`() {
    expect(listOf(0.4, 19.0, 86400.0).map { reach(KUKU, it) }).toEqual(listOf(0, 3, 600))
  }

  @Test
  fun `counts the stream a stopped part left unread as time away`(
    @TempDir tmp: Path,
  ) {
    Files.createFile(tmp.resolve("raw.mp3"))
    val started = now() - 600
    val status = Status(RecordingState.RECORDING, KUKU, started, started + 1200, 1200.0, listOf(Part("raw.mp3", 1.4, 0.0, 60.0)))
    expect(away(tmp, status)).toEqualWithErrorTolerance(558.0, 1.0)
  }

  @Test
  fun `reaches back only in a live playlist`() {
    expect(reach("/recordings/keskpäevatund.mp3", 19.0)).toEqual(0)
  }

  @Test
  fun `asks ffmpeg for the missed segments and one more`() {
    expect(command(KUKU, 30.0, 4, Path.of("raw-part-002")).joinToString(" "))
      .toContain("-rw_timeout 15000000 -m3u8_hold_counters 10 -live_start_index -7 -t 60.000 -i $KUKU")
  }

  @Test
  fun `starts a new part at the live edge`() {
    expect(command(KUKU, 30.0, 0, Path.of("raw")).joinToString(" "))
      .toContain("-rw_timeout 15000000 -m3u8_hold_counters 10 -t 30.000 -i $KUKU")
  }

  @Test
  fun `leaves the hold counters out of an MP3 input`() {
    expect(command("/recordings/in.mp3", 30.0, 0, Path.of("raw")).joinToString(" ")).notToContain("m3u8_hold_counters")
  }

  @Test
  fun `ends the part on its own when the playlist stops growing`(
    @TempDir tmp: Path,
  ) {
    val source = "${stale(tmp.resolve("stream.m3u8"))}"
    val ffmpeg =
      ProcessBuilder(command(source, 600.0, 0, tmp.resolve("raw")))
        .redirectOutput(Redirect.DISCARD)
        .redirectError(Redirect.DISCARD)
        .start()
    val ended =
      try {
        ffmpeg.waitFor(30, TimeUnit.SECONDS)
      } finally {
        ffmpeg.destroyForcibly()
      }
    expect(ended).toEqual(true)
  }

  @Test
  fun `runs ffmpeg from an absolute path`() {
    expect(Path.of(command(KUKU, 30.0, 0, Path.of("raw")).first()).isAbsolute).toEqual(true)
  }

  @Test
  fun `asks ffmpeg for only the time left in a resumed live part`() {
    val status = Status(RecordingState.RECORDING, KUKU, 1792832400.0, 1792836000.0, 3600.0, listOf(Part("raw.mp3", 1.4, 0.0, 60.0)))
    expect(length(status, 30.0, 4)).toEqual(30.0)
  }

  @Test
  fun `asks ffmpeg for only the time left when a first live part reaches back`() {
    val status = Status(RecordingState.RECORDING, KUKU, 1792832400.0, 1792836000.0, 3600.0)
    expect(length(status, 1800.0, 300)).toEqual(1800.0)
  }

  @Test
  fun `asks ffmpeg for the whole file again after a stop`() {
    val status = Status(RecordingState.RECORDING, "keskpäevatund.mp3", 0.0, 720.0, 720.0, listOf(Part("raw.mp3", 0.0, 0.0, 300.0)))
    expect(length(status, 690.0, 0)).toEqual(720.0)
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
    expect(status(adopted(tmp)).parts.map { it.file }).toEqual(listOf("raw.mp3"))
  }

  @Test
  fun `marks a recording failed when recording it throws`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("raadio")
    prepare(directory, KUKU, 0.0)
    Files.createFile(directory.resolve("raw.mp3"))
    val failed = runCatching { record(directory, CountDownLatch(1)) }.isFailure
    expect(Pair(failed, status(directory).state)).toEqual(Pair(true, RecordingState.FAILED))
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
  fun `writes no station key into a status without a station`(
    @TempDir tmp: Path,
  ) {
    prepare(tmp.resolve("raadio"), KUKU, 0.0)
    expect(Files.readString(tmp.resolve("raadio/status.json"))).notToContain("\"station\"")
  }

  @Test
  fun `keeps the station in the status through a finished recording`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("raadio")
    prepare(directory, "${tone(tmp.resolve("in.mp3"), 2.0)}", 2.0, station = "ARIPAEV")
    record(directory, CountDownLatch(1))
    expect(status(directory).station).toEqual("ARIPAEV")
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
  fun `refuses a directory that is not its own, naming why`(
    @TempDir tmp: Path,
  ) {
    val json =
      """{"state": "captured_needs_review", "source": "$KUKU", "startedAt": 1792832100.0, "endsAt": 1792836300.0, "seconds": 4200.0, "parts": []}"""
    Files.writeString(tmp.resolve("status.json"), json)
    expect { prepare(tmp, KUKU, 3.0) }.toThrow<RecordingRefusedException>().cause<JacksonException>()
  }

  @Test
  fun `refuses a directory that holds a recording of another source`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("raadio")
    prepare(directory, KUKU, 0.0)
    expect { prepare(directory, "http://127.0.0.1:8770/stream.m3u8", 0.0) }.toThrow<RecordingRefusedException>().messageToContain("not of")
  }

  @Test
  fun `ends at its end time when the source never answers`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("raadio")
    val port = ServerSocket(0).use { it.localPort }
    prepare(directory, "http://127.0.0.1:$port/stream.m3u8", 3.0)
    val recorder = thread { record(directory, CountDownLatch(1)) }
    recorder.join(10_000)
    val status = status(directory)
    expect(Triple(recorder.isAlive, status.state, status.parts)).toEqual(Triple(false, RecordingState.FAILED, emptyList()))
  }

  @Test
  fun `tries once for the lost tail of an ended recording whose source never answers`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("raadio")
    val port = ServerSocket(0).use { it.localPort }
    prepare(directory, "http://127.0.0.1:$port/stream.m3u8", 55.0, started = now() - 60)
    Files.createFile(directory.resolve("raw.mp3"))
    write(directory.resolve("status.json"), status(directory).copy(parts = listOf(Part("raw.mp3", 1.4, 0.0, 10.0))))
    val recorder = thread { record(directory, CountDownLatch(1)) }
    recorder.join(20_000)
    expect(Pair(recorder.isAlive, status(directory).state)).toEqual(Pair(false, RecordingState.PARTIAL))
  }

  @Test
  fun `moves the start of a recording with no parts to an hour before it is recorded`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("raadio")
    val port = ServerSocket(0).use { it.localPort }
    prepare(directory, "http://127.0.0.1:$port/stream.m3u8", 3650.0, started = now() - 3700)
    val recorded = now()
    record(directory, CountDownLatch(1))
    val status = status(directory)
    expect(Pair(abs(status.startedAt + LOOKBACK - recorded) <= 1.0, status.seconds))
      .toEqual(Pair(true, round3(status.endsAt - status.startedAt)))
  }

  private fun recorded(tmp: Path): Path =
    replay(tmp).use { station ->
      tmp.resolve("keskpäevatund").also {
        prepare(it, station.url, 12.0)
        record(it, CountDownLatch(1))
      }
    }

  private fun adopted(tmp: Path): Path {
    val directory = tmp.resolve("raadio")
    prepare(directory, KUKU, 0.0)
    Files.createFile(directory.resolve("raw.mp3"))
    Files.writeString(directory.resolve("raw.log"), "  Duration: N/A, start: 1.400000, bitrate: N/A\n")
    record(directory, CountDownLatch(1))
    return directory
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
