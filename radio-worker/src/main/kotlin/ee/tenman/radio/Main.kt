package ee.tenman.radio

import java.lang.ProcessBuilder.Redirect
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.min
import kotlin.system.exitProcess
import kotlin.time.Duration

private const val MODEL = "/opt/zipformer"
private const val TAGGER = "/opt/ced-tiny"
private val FLAGS =
  mapOf(
    "station" to setOf("--host", "--port", "--from", "--speed"),
    "run" to setOf("--source", "--duration", "--start", "--programme", "--out", "--model", "--tagger", "--threads"),
    "planned" to setOf("--recordings", "--station", "--source", "--model", "--tagger", "--threads"),
    "sounds" to setOf("--tagger"),
  )
private val ARGUMENT = mapOf("station" to "recording", "sounds" to "directory")

private class UsageException(
  message: String,
) : RuntimeException(message)

private data class Options(
  val arguments: List<String> = emptyList(),
  val values: Map<String, List<String>> = emptyMap(),
) {
  fun text(flag: String): String? = values[flag]?.last()

  fun path(
    flag: String,
    default: String,
  ): Path = Path.of(text(flag) ?: default)

  fun number(flag: String): Double? = text(flag)?.let { it.toDoubleOrNull() ?: usage("$flag takes a number, not $it") }

  fun whole(flag: String): Int? = text(flag)?.let { it.toIntOrNull() ?: usage("$flag takes a whole number, not $it") }
}

fun main(args: Array<String>) {
  exitProcess(launch(args.toList(), ::ZipformerEars))
}

internal fun launch(
  args: List<String>,
  ears: (Path, Int) -> Ears,
): Int {
  val stop = CountDownLatch(1)
  val done = CountDownLatch(1)
  Runtime.getRuntime().addShutdownHook(
    Thread {
      stop.countDown()
      done.await(5, TimeUnit.MINUTES)
    },
  )
  return try {
    command(args, ears, stop)
  } finally {
    done.countDown()
  }
}

internal fun command(
  args: List<String>,
  ears: (Path, Int) -> Ears,
  stop: CountDownLatch,
): Int =
  runCatching { dispatch(args, ears, stop) }.getOrElse { error ->
    if (error !is UsageException && error !is RecordingRefusedException) throw error
    System.err.println("radio-worker: error: ${error.message}")
    2
  }

internal fun capture(
  directory: Path,
  source: String,
  seconds: Double,
  load: () -> Ears,
  tagger: Path,
  stop: CountDownLatch,
  programmes: List<String>? = null,
  started: Double? = null,
  slot: Slot? = null,
  station: String? = null,
): RecordingState {
  prepare(directory, source, seconds, programmes, started, slot, station)
  hold(directory).use {
    val recorder = thread(name = "recorder") { record(directory, stop) }
    val transcribed = runCatching { transcribe(directory, load, tagger, recorder::isAlive, stop) }
    recorder.join()
    if (stop.count > 0L) transcribed.getOrThrow()
    transcribed.onFailure { System.err.println("Transcribing $directory failed: $it") }
  }
  return read<Status>(directory.resolve("status.json")).state
}

internal fun span(
  source: String,
  duration: Double?,
): Double {
  if (!Files.isRegularFile(Path.of(source))) return duration ?: usage("--duration is required when --source is a stream")
  val ffprobe =
    ProcessBuilder(tool("ffprobe"), "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", source)
      .redirectError(Redirect.INHERIT)
      .start()
  val probed = ffprobe.inputReader().readText()
  check(ffprobe.waitFor() == 0) { "ffprobe could not read $source, exit ${ffprobe.exitValue()}" }
  return min(duration ?: Double.POSITIVE_INFINITY, probed.trim().toDouble())
}

internal fun moment(value: String): Double {
  val time =
    runCatching { OffsetDateTime.parse(value).toInstant() }
      .recoverCatching { LocalDateTime.parse(value).atZone(TALLINN).toInstant() }
      .getOrElse { usage("--start takes an ISO time, not $value") }
  return time.toEpochMilli() / 1000.0
}

private fun dispatch(
  args: List<String>,
  ears: (Path, Int) -> Ears,
  stop: CountDownLatch,
): Int {
  val name = args.firstOrNull() ?: usage("a command is required: station, run, planned or sounds")
  val flags = FLAGS[name] ?: usage("$name is not a command, use station, run, planned or sounds")
  val options = parse(args.drop(1), flags)
  val wanted = listOfNotNull(ARGUMENT[name])
  if (options.arguments.size > wanted.size) usage("unrecognized argument ${options.arguments[wanted.size]}")
  if (options.arguments.size < wanted.size) usage("$name needs a ${wanted.first()}")
  return when (name) {
    "station" -> broadcast(options, stop)
    "run" -> recordOnce(options, ears, stop)
    "planned" -> follow(options, ears, stop)
    else -> {
      hear(Path.of(options.arguments.single()), options.path("--tagger", TAGGER))
      0
    }
  }
}

private tailrec fun parse(
  rest: List<String>,
  flags: Set<String>,
  options: Options = Options(),
): Options {
  val token = rest.firstOrNull() ?: return options
  if (!token.startsWith("--")) return parse(rest.drop(1), flags, options.copy(arguments = options.arguments + token))
  if (token !in flags) usage("unrecognized argument $token")
  val value = rest.getOrNull(1) ?: usage("$token needs a value")
  return parse(rest.drop(2), flags, options.copy(values = options.values + (token to options.values[token].orEmpty() + value)))
}

private fun broadcast(
  options: Options,
  stop: CountDownLatch,
): Int {
  val recording = Path.of(options.arguments.single())
  if (!Files.isRegularFile(recording)) usage("$recording is not a file")
  val host = options.text("--host") ?: "127.0.0.1"
  val port = options.whole("--port") ?: 8770
  val from = options.number("--from") ?: 0.0
  val speed = options.number("--speed") ?: 1.0
  val hls = Files.createTempDirectory("radio-station")
  try {
    Station.open(recording, hls, host, port, from, speed).use { station ->
      println("On air at http://$host:${station.address.port}/stream.m3u8")
      generateSequence { stop.await(1, TimeUnit.SECONDS) || station.awaitOffAir(Duration.ZERO) }.first { it }
    }
  } finally {
    hls.toFile().deleteRecursively()
  }
  return 0
}

private fun recordOnce(
  options: Options,
  ears: (Path, Int) -> Ears,
  stop: CountDownLatch,
): Int {
  val out = Path.of(options.text("--out") ?: usage("--out is required"))
  val model = options.path("--model", MODEL)
  if (!Files.isRegularFile(model.resolve("encoder.int8.onnx"))) usage("$model holds no Zipformer encoder.int8.onnx")
  val tagger = options.path("--tagger", TAGGER)
  if (!Files.isRegularFile(tagger.resolve("model.int8.onnx"))) usage("$tagger holds no CED-tiny model.int8.onnx")
  val source = options.text("--source") ?: KUKU
  val threads = options.whole("--threads") ?: 1
  val started = options.text("--start")?.let(::moment)
  val seconds = span(source, options.number("--duration"))
  val state = capture(out, source, seconds, { ears(model, threads) }, tagger, stop, options.values["--programme"], started)
  if (stop.count == 0L) {
    System.err.println("Stopped; run again with --out $out to resume")
    return 130
  }
  println("${state.name.lowercase()}: ${out.resolve("transcript.json")}")
  return if (state == RecordingState.FAILED) 1 else 0
}

private fun follow(
  options: Options,
  ears: (Path, Int) -> Ears,
  stop: CountDownLatch,
): Int {
  val station = options.text("--station")
  val schedule = station?.let { SCHEDULES[it] ?: usage("--station takes ARIPAEV, not $it") } ?: Schedule()
  if (station != null && options.text("--source") == null) usage("--station $station needs --source")
  val source = options.text("--source") ?: KUKU
  val model = options.path("--model", MODEL)
  val threads = options.whole("--threads") ?: 1
  val tagger = options.path("--tagger", TAGGER)
  val code =
    planned(
      options.path("--recordings", "/recordings"),
      { directory, started, seconds, programmes, slot ->
        capture(directory, source, seconds, { ears(model, threads) }, tagger, stop, programmes, started, slot, station)
      },
      ::now,
      { stop.await((it * 1000).toLong(), TimeUnit.MILLISECONDS) },
      schedule,
    )
  if (code == 130) System.err.println("Stopped; the next start resumes an unfinished recording")
  return code
}

private fun usage(message: String): Nothing = throw UsageException(message)
