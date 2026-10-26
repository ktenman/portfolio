package ee.tenman.radio

import ch.tutteli.atrium.api.fluent.en_GB.toBeAnInstanceOf
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

private const val CED_TINY = "ced-tiny"

private fun model(tmp: Path): String {
  val directory = Files.createDirectories(tmp.resolve("zipformer"))
  Files.createFile(directory.resolve("encoder.int8.onnx"))
  return "$directory"
}

private fun runs(
  vararg args: String,
  ears: () -> Ears = { StubEars(60.0) },
  stop: CountDownLatch = CountDownLatch(1),
): Int = command(args.toList(), { _, _ -> ears() }, stop)

private fun interrupted(
  tmp: Path,
  directory: Path,
  ears: () -> Ears,
): Int {
  val model = model(tmp)
  val stop = CountDownLatch(1)
  return replay(tmp, 40.0).use { station ->
    val args = arrayOf("run", "--source", station.url, "--duration", "30", "--out", "$directory", "--model", model, "--tagger", CED_TINY)
    val running = CompletableFuture.supplyAsync { runs(*args, ears = ears, stop = stop) }
    try {
      waitFor { samples(directory.resolve("raw.pcm")) >= 2 * RATE }
    } finally {
      stop.countDown()
    }
    running.get(30, TimeUnit.SECONDS)
  }
}

private fun child(
  tmp: Path,
  source: String,
  directory: Path,
): Process =
  ProcessBuilder(
    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
    "--enable-native-access=ALL-UNNAMED",
    "-cp",
    System.getProperty("radio.classpath"),
    "ee.tenman.radio.StubMainKt",
    "run",
    "--source",
    source,
    "--duration",
    "200",
    "--out",
    "$directory",
    "--model",
    model(tmp),
    "--tagger",
    CED_TINY,
  ).redirectErrorStream(true)
    .redirectOutput(tmp.resolve("child.log").toFile())
    .start()

class MainTest {
  @Test
  fun `run records and transcribes a replay`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("rec")
    replay(tmp).use { station ->
      runs("run", "--source", station.url, "--duration", "4", "--out", "$directory", "--model", model(tmp), "--tagger", CED_TINY)
    }
    expect(Pair(status(directory).state, transcript(directory).words.size)).toEqual(Pair(RecordingState.CAPTURED, 1))
  }

  @Test
  fun `run records a file once to its end at the time it went on air`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("rec")
    val source = "${tone(tmp.resolve("kesk.mp3"), 13.0)}"
    runs("run", "--source", source, "--start", "2026-10-17T11:55", "--out", "$directory", "--model", model(tmp), "--tagger", CED_TINY)
    val status = status(directory)
    expect(Triple(status.state, status.parts.size, status.startedAt)).toEqual(Triple(RecordingState.CAPTURED, 1, 1792227300.0))
  }

  @Test
  fun `a stream is recorded for the duration it is given`() {
    expect(span("http://127.0.0.1:9/stream.m3u8", 600.0)).toEqual(600.0)
  }

  @Test
  fun `a time without an offset is read in Tallinn`() {
    expect(moment("2026-10-17T11:55")).toEqual(1792227300.0)
  }

  @Test
  fun `a time in the autumn overlap takes the earlier offset`() {
    expect(moment("2026-10-25T03:30")).toEqual(1792888200.0)
  }

  @Test
  fun `run only finishes the transcript of an ended recording`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("rec")
    val before =
      replay(tmp).use { station ->
        prepare(directory, station.url, 4.0)
        record(directory, CountDownLatch(1))
        val before = sha256(directory.resolve("raw.mp3"))
        write(directory.resolve("status.json"), status(directory).copy(endsAt = now() + 60))
        runs("run", "--source", station.url, "--duration", "60", "--out", "$directory", "--model", model(tmp), "--tagger", CED_TINY)
        before
      }
    val after =
      Triple(
        status(directory).parts.map { it.file },
        sha256(directory.resolve("raw.mp3")),
        Files.exists(directory.resolve("transcript.json")),
      )
    expect(after).toEqual(Triple(listOf("raw.mp3"), before, true))
  }

  @Test
  fun `run stops the recording when interrupted`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("rec")
    val code = interrupted(tmp, directory) { StubEars(60.0) }
    val status = status(directory)
    expect(Triple(code, status.state, status.parts.first().durationSeconds!! < 20.0)).toEqual(Triple(130, RecordingState.RECORDING, true))
  }

  @Test
  fun `run stops the recording when interrupted after a transcriber error`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("rec")
    val code = interrupted(tmp, directory) { error("the model is broken") }
    expect(Pair(code, status(directory).state)).toEqual(Pair(130, RecordingState.RECORDING))
  }

  @Test
  fun `run refuses a directory that is not a recording`(
    @TempDir tmp: Path,
  ) {
    val directory = Files.createDirectories(tmp.resolve("rec"))
    val json =
      """{"state": "captured_needs_review", "source": "$KUKU", "startedAt": 1792832100.0, "endsAt": 1792836300.0, "seconds": 4200.0, "parts": []}"""
    Files.writeString(directory.resolve("status.json"), json)
    val code = runs("run", "--out", "$directory", "--duration", "3", "--model", model(tmp), "--tagger", CED_TINY)
    expect(Pair(code, Files.readString(directory.resolve("status.json")))).toEqual(Pair(2, json))
  }

  @Test
  fun `sounds hears a recording already transcribed`(
    @TempDir tmp: Path,
  ) {
    writeRecording(tmp, listOf(Written("raw.mp3", 0.0, noise(2.0), true)), RecordingState.CAPTURED)
    Files.writeString(tmp.resolve("transcript.json"), "{}")
    expect(Pair(runs("sounds", "$tmp", "--tagger", CED_TINY), Files.exists(tmp.resolve("sounds.json")))).toEqual(Pair(0, true))
  }

  @Test
  fun `a sigterm during a capture leaves the recording for the next start to resume`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("rec")
    val exited =
      replay(tmp, 240.0).use { station ->
        val child = child(tmp, station.url, directory)
        try {
          waitFor { samples(directory.resolve("raw.pcm")) >= 2 * RATE }
          child.destroy()
          child.waitFor(30, TimeUnit.SECONDS)
        } finally {
          child.destroyForcibly()
        }
      }
    val status = status(directory)
    val left = Triple(status.state, status.parts.first().durationSeconds != null, recorders(directory.resolve("raw.mp3")).isEmpty())
    expect(Pair(exited, left)).toEqual(Pair(true, Triple(RecordingState.RECORDING, true, true)))
  }

  @Test
  fun `refuses a directory that another worker process records`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("rec")
    val refusal =
      replay(tmp, 240.0).use { station ->
        val child = child(tmp, station.url, directory)
        try {
          waitFor { samples(directory.resolve("raw.pcm")) >= 2 * RATE }
          runCatching { hold(directory).close() }.exceptionOrNull()
        } finally {
          child.destroy()
          child.waitFor(30, TimeUnit.SECONDS)
          child.destroyForcibly()
        }
      }
    expect(refusal).toBeAnInstanceOf<RecordingRefusedException>()
  }

  @Test
  fun `run names every programme it is given in the status`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("rec")
    val source = "${tone(tmp.resolve("kesk.mp3"), 2.0)}"
    val programmes = arrayOf("--programme", "Keskpäevatund", "--programme", "Uudised")
    runs("run", "--source", source, *programmes, "--out", "$directory", "--model", model(tmp), "--tagger", CED_TINY)
    expect(status(directory).programmes).toEqual(listOf("Keskpäevatund", "Uudised"))
  }

  @Test
  fun `every usage error exits 2 without creating the output directory`(
    @TempDir tmp: Path,
  ) {
    val out = "${tmp.resolve("rec")}"
    val model = model(tmp)
    val file = "${tone(tmp.resolve("kesk.mp3"), 2.0)}"
    val run = listOf("run", "--out", out, "--model", model)
    val errors =
      listOf(
        emptyList<String>(),
        listOf("record", "--out", out),
        run + listOf("--source", file, "--tagger", CED_TINY, "--duraton", "60"),
        run + listOf("--source", file, "--tagger", CED_TINY, "pooleli"),
        run + listOf("--source", file, "--tagger", CED_TINY, "--duration"),
        run + listOf("--source", file, "--tagger", CED_TINY, "--duration", "kuus"),
        listOf("run", "--source", file, "--model", model, "--tagger", CED_TINY),
        listOf("run", "--out", out, "--model", "$tmp", "--source", file, "--tagger", CED_TINY),
        run + listOf("--source", file, "--tagger", "$tmp"),
        run + listOf("--source", "http://127.0.0.1:8770/stream.m3u8", "--tagger", CED_TINY),
        listOf("station", "${tmp.resolve("puudub.mp3")}"),
      )
    val codes = errors.map { runs(*it.toTypedArray()) }
    expect(Pair(codes, Files.exists(tmp.resolve("rec")))).toEqual(Pair(List(errors.size) { 2 }, false))
  }

  @Test
  fun `a slow recognizer does not shorten the recording`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("rec")
    val slow = { StubEars(60.0, 2.seconds) }
    val model = model(tmp)
    replay(tmp).use { station ->
      runs("run", "--source", station.url, "--duration", "6", "--out", "$directory", "--model", model, "--tagger", CED_TINY, ears = slow)
    }
    val status = status(directory)
    val captured =
      Triple(status.state, abs(status.parts.first().durationSeconds!! - 6.0) <= 0.2, Files.exists(directory.resolve("transcript.json")))
    expect(captured).toEqual(Triple(RecordingState.CAPTURED, true, true))
  }

  @Test
  fun `a model that fails to load leaves the recording captured for a rerun`(
    @TempDir tmp: Path,
  ) {
    val directory = tmp.resolve("rec")
    val rerun =
      replay(tmp).use { station ->
        runCatching { capture(directory, station.url, 4.0, { error("the model is broken") }, Path.of(CED_TINY), CountDownLatch(1)) }
        val after = status(directory).state
        capture(directory, station.url, 4.0, { StubEars(60.0) }, Path.of(CED_TINY), CountDownLatch(1))
        Pair(after, Files.exists(directory.resolve("transcript.json")))
      }
    expect(rerun).toEqual(Pair(RecordingState.CAPTURED, true))
  }

  @Test
  fun `planned records a capture from its start until interrupted`(
    @TempDir tmp: Path,
  ) {
    val recordings = Files.createDirectories(tmp.resolve("recordings"))
    val directory = recordings.resolve("2026-10-25-1200-kuku")
    val model = model(tmp)
    val stop = CountDownLatch(1)
    val (code, start) =
      replay(tmp, 40.0).use { station ->
        val start = now().toLong()
        write(recordings.resolve("plan.json"), listOf(Capture("${directory.fileName}", listOf("Kuku"), start, start + 30)))
        val running =
          CompletableFuture.supplyAsync {
            runs("planned", "--recordings", "$recordings", "--source", station.url, "--model", model, "--tagger", CED_TINY, stop = stop)
          }
        try {
          waitFor { samples(directory.resolve("raw.pcm")) >= 2 * RATE }
        } finally {
          stop.countDown()
        }
        Pair(running.get(30, TimeUnit.SECONDS), start)
      }
    val status = status(directory)
    expect(Triple(code, status.state, status.startedAt)).toEqual(Triple(130, RecordingState.RECORDING, start.toDouble()))
  }

  @Test
  fun `station returns once stopped`(
    @TempDir tmp: Path,
  ) {
    expect(runs("station", "${tone(tmp.resolve("in.mp3"), 2.0)}", "--port", "0", stop = CountDownLatch(0))).toEqual(0)
  }
}
