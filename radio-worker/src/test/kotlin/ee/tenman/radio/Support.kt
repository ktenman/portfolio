package ee.tenman.radio

import tools.jackson.module.kotlin.readValue
import java.lang.ProcessBuilder.Redirect
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.toJavaDuration

class Written(
  val file: String,
  val offset: Double,
  val samples: ShortArray,
  val finished: Boolean,
)

fun tone(
  path: Path,
  seconds: Double,
): Path {
  val wave = "if(between(mod(t,10),7,7.4),0,0.3*sin(2*PI*440*t))"
  val command =
    listOf(
      "ffmpeg",
      "-hide_banner",
      "-loglevel",
      "error",
      "-f",
      "lavfi",
      "-i",
      "aevalsrc='$wave|$wave':s=48000:d=$seconds",
      "-c:a",
      "libmp3lame",
      "-b:a",
      "256k",
      path.toString(),
    )
  val process = ProcessBuilder(command).redirectErrorStream(true).start()
  val output = process.inputReader().readText()
  check(process.waitFor() == 0) { "FFmpeg could not make $path: ${output.trim()}" }
  return path
}

fun replay(
  directory: Path,
  seconds: Double = 13.0,
  from: Double = 0.0,
  speed: Double = 4.0,
): Station =
  Station.open(
    tone(directory.resolve("in.mp3"), seconds),
    Files.createDirectory(directory.resolve("hls")),
    port = 0,
    from = from,
    speed = speed,
  )

val Station.url: String
  get() = "http://127.0.0.1:${address.port}/stream.m3u8"

fun get(url: String): HttpResponse<String> =
  HttpClient.newHttpClient().use {
    it.send(HttpRequest.newBuilder(URI(url)).timeout(30.seconds.toJavaDuration()).build(), HttpResponse.BodyHandlers.ofString())
  }

fun waitFor(
  seconds: Double = 30.0,
  condition: () -> Boolean,
) {
  val deadline = TimeSource.Monotonic.markNow() + seconds.seconds
  while (!condition()) {
    check(deadline.hasNotPassedNow()) { "The condition was not met within $seconds s" }
    Thread.sleep(100)
  }
}

fun status(directory: Path): Status = read(directory.resolve("status.json"))

fun firstSilence(
  pcm: Path,
  after: Double,
): Double {
  val audio = ByteBuffer.wrap(Files.readAllBytes(pcm)).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
  val hop = RATE / 100
  val quiet =
    (0 until audio.limit() / hop).first { index ->
      index / 100.0 > after && sqrt((0 until hop).sumOf { audio[index * hop + it].toDouble().pow(2) } / hop) < 50
    }
  return quiet / 100.0
}

fun caughtUp(
  directory: Path,
  playlist: Path,
): Boolean {
  val listed = if (Files.exists(playlist)) Files.readAllLines(playlist).count { it.endsWith(".ts") } else 0
  return listed >= 4 && seconds(directory.resolve("raw.pcm")) >= 6 * listed - 6.5
}

fun recorders(mp3: Path): List<ProcessHandle> =
  ProcessHandle.allProcesses().toList().filter { "$mp3" in it.info().commandLine().orElse("") }

fun silence(
  path: Path,
  seconds: Double,
): Path = Files.write(path, ByteArray(2 * (seconds * RATE).toInt()))

fun arpeggio(
  path: Path,
  seconds: Double,
): Path {
  val expression =
    """st(0,261.63*pow(2,if(eq(floor(mod(4*t,4)),0),0,if(eq(floor(mod(4*t,4)),1),4,if(eq(floor(mod(4*t,4)),2),7,12)))/12));0.3*(sin(2*PI*ld(0)*t)+0.5*sin(4*PI*ld(0)*t)+0.33*sin(6*PI*ld(0)*t)+0.25*sin(8*PI*ld(0)*t))*exp(-6*mod(t,0.25))"""
  return render(path, "-f", "lavfi", "-i", "aevalsrc='$expression':s=16000:d=$seconds", "-ac", "1", "-ar", "16000", "-f", "s16le")
}

fun close(
  actual: List<Double>,
  expected: List<Double>,
  tolerance: Double = 0.001,
): Boolean = actual.size == expected.size && actual.zip(expected).all { (value, target) -> abs(value - target) <= tolerance }

fun listing(
  file: String,
  offset: Double,
  seconds: Double,
  finished: Boolean,
): Part =
  if (finished) {
    Part(file, 1.4 + offset, offset, seconds, "0".repeat(64), 0)
  } else {
    Part(file, 1.4 + offset, offset)
  }

fun writeRecording(
  directory: Path,
  parts: List<Written>,
  state: RecordingState,
) {
  val listed = parts.map { listing(it.file, it.offset, it.samples.size / RATE.toDouble(), it.finished) }
  parts.zip(listed).forEach { (written, part) -> Files.write(pcm(directory, part), bytes(written.samples)) }
  write(directory.resolve("status.json"), Status(state, "http://127.0.0.1:9/stream.m3u8", 0.0, 0.0, 0.0, listed))
}

private fun bytes(samples: ShortArray): ByteArray =
  ByteBuffer
    .allocate(2 * samples.size)
    .order(ByteOrder.LITTLE_ENDIAN)
    .apply { asShortBuffer().put(samples) }
    .array()

private fun render(
  path: Path,
  vararg arguments: String,
): Path {
  val command = listOf("ffmpeg", "-hide_banner", "-loglevel", "error", *arguments, path.toString())
  val process = ProcessBuilder(command).redirectOutput(Redirect.INHERIT).redirectError(Redirect.INHERIT).start()
  check(process.waitFor() == 0) { "FFmpeg could not write $path, exit ${process.exitValue()}" }
  return path
}

fun noise(seconds: Double): ShortArray {
  val random = Random(7)
  return ShortArray((seconds * RATE).toInt()) { random.nextInt(-8000, 8000).toShort() }
}

fun transcript(directory: Path): Transcript = read(directory.resolve("transcript.json"))

fun chunks(directory: Path): List<Chunk> =
  Files
    .readString(directory.resolve("transcript.jsonl"))
    .split("\n")
    .dropLast(1)
    .map { mapper.readValue<Chunk>(it) }

fun head(path: Path): List<Pair<String, Any?>> =
  mapper
    .readValue<Map<String, Any?>>(Files.readString(path))
    .entries
    .take(2)
    .map { it.key to it.value }

class StubEars(
  private val every: Double,
  private val delay: Duration = Duration.ZERO,
) : Ears {
  override val name = "stub"
  override val revision = "stub revision"
  private val lengths = CopyOnWriteArrayList<Double>()
  private val distinct = ConcurrentHashMap.newKeySet<Float>()
  val heard: List<Double>
    get() = lengths.toList()
  val values: Set<Float>
    get() = distinct.toSet()

  override fun open(): Ear {
    lengths += 0.0
    return StubEar(lengths.size)
  }

  private inner class StubEar(
    private val number: Int,
  ) : Ear {
    private var count = 0
    private val length: Double
      get() = count / RATE.toDouble()

    override fun hear(samples: FloatArray): List<Utterance> {
      Thread.sleep(delay.inWholeMilliseconds)
      val passed = floor(length / every).toInt()
      count += samples.size
      distinct.addAll(samples.asList())
      lengths[number - 1] = length
      return (passed + 1..floor(length / every).toInt()).map { Utterance(it * every, emptyList()) }
    }

    override fun end(): List<Segment> = listOf(Segment(0.0, length, "lõik $number", listOf(Word(0.0, length, " lõik$number"))))

    override fun close() = Unit
  }
}
