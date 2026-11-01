package ee.tenman.radio

import java.lang.ProcessBuilder.Redirect
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

private const val STATUS = "status.json"
private const val SEGMENT = 6
private const val BEHIND = 3
private val START = Regex("""start: (-?\d+\.\d+)""")
private val WRAP = 2.0.pow(33) / 90000

fun prepare(
  directory: Path,
  source: String,
  seconds: Double,
  programmes: List<String>? = null,
  started: Double? = null,
  slot: Slot? = null,
  station: String? = null,
) {
  if (Files.exists(directory)) {
    claim(directory, source)
    return
  }
  Files.createDirectories(directory)
  val clock = now()
  val start = started ?: clock
  val end = (if (live(source)) start else clock) + seconds
  val named = programmes?.takeIf { it.isNotEmpty() }
  store(
    directory,
    Status(RecordingState.RECORDING, source, round3(start), round3(end), seconds, programmes = named, slot = slot, station = station),
  )
}

fun hold(directory: Path): AutoCloseable {
  val channel = FileChannel.open(directory.resolve("worker.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
  val lock = runCatching { channel.tryLock() }
  if (lock.getOrNull() != null) return channel
  channel.close()
  val error = lock.exceptionOrNull()
  if (error != null && error !is OverlappingFileLockException) throw error
  throw RecordingRefusedException("$directory is being recorded by another worker")
}

fun record(
  directory: Path,
  stop: CountDownLatch,
) {
  val status = read<Status>(directory.resolve(STATUS))
  if (status.state != RecordingState.RECORDING) return
  val recorded =
    runCatching { capture(directory, complete(directory, adopt(directory, status)), stop) }
      .onFailure { error ->
        runCatching { store(directory, read<Status>(directory.resolve(STATUS)).copy(state = RecordingState.FAILED)) }
          .onFailure(error::addSuppressed)
      }.getOrThrow()
  if (stop.count == 0L) return
  val missing = lost(recorded.seconds, recorded.parts)
  store(directory, recorded.copy(state = verdict(recorded.parts, missing), lostSeconds = missing))
}

internal fun register(
  directory: Path,
  status: Status,
  stem: String,
): Status {
  val file = "$stem.mp3"
  if (status.parts.any { it.file == file } || Files.notExists(directory.resolve(file))) return status
  val log = String(Files.readAllBytes(directory.resolve("$stem.log")), StandardCharsets.ISO_8859_1)
  val match = START.findAll(log).lastOrNull() ?: return status
  val start = match.groupValues[1].toDouble()
  val origin = status.parts.firstOrNull()?.streamStart ?: start
  val part = Part(file, start, round3(Math.IEEEremainder(start - origin, WRAP)))
  return store(directory, status.copy(parts = status.parts + part)).also { event(directory, "$file started at ${part.offsetSeconds} s") }
}

internal fun lost(
  seconds: Double,
  parts: List<Part>,
): Long {
  val (covered, holes) =
    parts.sortedBy { it.offsetSeconds }.fold(0.0 to 0.0) { (covered, holes), part ->
      max(covered, part.offsetSeconds + (part.durationSeconds ?: 0.0)) to holes + max(0.0, part.offsetSeconds - covered)
    }
  return round(holes + max(0.0, seconds - covered)).toLong()
}

internal fun verdict(
  parts: List<Part>,
  lost: Long,
): RecordingState =
  when {
    parts.isEmpty() -> RecordingState.FAILED
    lost > 0 -> RecordingState.PARTIAL
    else -> RecordingState.CAPTURED
  }

internal fun reach(
  source: String,
  away: Double,
): Int {
  if (!live(source)) return 0
  return (away / SEGMENT).roundToInt().coerceIn(0, (LOOKBACK / SEGMENT).toInt())
}

internal fun command(
  source: String,
  seconds: Double,
  reach: Int,
  base: Path,
): List<String> =
  listOf(tool("ffmpeg"), "-hide_banner", "-nostdin", "-nostats", "-loglevel", "info", "-n", "-rw_timeout", "15000000") +
    (if (live(source)) listOf("-m3u8_hold_counters", "10") else emptyList()) +
    (if (reach > 0) listOf("-live_start_index", "${-(BEHIND + reach)}") else emptyList()) +
    listOf("-t", "%.3f".format(Locale.ROOT, seconds + extra(reach)), "-i", source) +
    listOf("-map", "0:a:0", "-c:a", "copy", "-flush_packets", "1", "$base.mp3") +
    listOf("-map", "0:a:0", "-ac", "1", "-ar", "$RATE", "-f", "s16le", "-flush_packets", "1", "$base.pcm")

private fun claim(
  directory: Path,
  source: String,
) {
  val status =
    runCatching { read<Status>(directory.resolve(STATUS)) }
      .getOrElse { throw RecordingRefusedException("$directory exists and holds no recording made by this worker", it) }
  if (status.source != source) throw RecordingRefusedException("$directory holds a recording of ${status.source}, not of $source")
}

private fun adopt(
  directory: Path,
  status: Status,
): Status =
  stems()
    .takeWhile { Files.exists(directory.resolve("$it.mp3")) }
    .fold(status) { current, stem -> register(directory, current, stem) }

private fun complete(
  directory: Path,
  status: Status,
): Status = status.parts.filter { it.durationSeconds == null }.fold(status) { current, part -> finish(directory, current, part.file, null) }

private tailrec fun capture(
  directory: Path,
  status: Status,
  stop: CountDownLatch,
): Status {
  val remaining = round3(status.endsAt - now())
  val reach = reach(status.source, away(directory, status))
  val tail = round(max(0.0, status.seconds - covered(status.parts)))
  if (stop.count == 0L || remaining + extra(reach) <= 0 || remaining <= 0 && tail == 0.0) return status
  val attempted = attempt(directory, trim(directory, status), remaining, reach, stop)
  val part = attempted.parts.drop(status.parts.size).firstOrNull()
  if (remaining <= 0 || part != null && (part.durationSeconds ?: 0.0) >= remaining + extra(reach) - 1) return attempted
  stop.await(2, TimeUnit.SECONDS)
  return capture(directory, attempted, stop)
}

internal fun away(
  directory: Path,
  status: Status,
): Double {
  val clock = now()
  val behind = clock - status.startedAt - covered(status.parts)
  val newest = status.parts.lastOrNull() ?: return behind
  return max(behind + BEHIND * SEGMENT, clock - Files.getLastModifiedTime(directory.resolve(newest.file)).toMillis() / 1000.0)
}

private fun trim(
  directory: Path,
  status: Status,
): Status {
  val earliest = round3(now() - LOOKBACK)
  if (!live(status.source) || status.parts.isNotEmpty() || status.startedAt >= earliest || earliest >= status.endsAt) return status
  return store(directory, status.copy(startedAt = earliest, seconds = round3(status.endsAt - earliest)))
}

private fun attempt(
  directory: Path,
  status: Status,
  remaining: Double,
  reach: Int,
  stop: CountDownLatch,
): Status {
  val stem = stems().first { Files.notExists(directory.resolve("$it.mp3")) }
  val ffmpeg =
    ProcessBuilder(command(status.source, length(status, remaining, reach), reach, directory.resolve(stem)))
      .redirectOutput(Redirect.INHERIT)
      .redirectError(Redirect.appendTo(directory.resolve("$stem.log").toFile()))
      .start()
  val followed =
    try {
      follow(directory, status, stem, ffmpeg, stop)
    } finally {
      ffmpeg.destroyForcibly()
      ffmpeg.waitFor()
    }
  val registered = register(directory, followed, stem)
  if (registered.parts.any { it.file == "$stem.mp3" }) return finish(directory, registered, "$stem.mp3", ffmpeg.exitValue())
  check(Files.notExists(directory.resolve("$stem.mp3"))) { "FFmpeg wrote $stem.mp3 but $stem.log does not say where the stream starts" }
  return registered
}

private tailrec fun follow(
  directory: Path,
  status: Status,
  stem: String,
  ffmpeg: Process,
  stop: CountDownLatch,
): Status {
  if (!ffmpeg.isAlive || stop.await(500, TimeUnit.MILLISECONDS)) return status
  return follow(directory, register(directory, status, stem), stem, ffmpeg, stop)
}

private fun finish(
  directory: Path,
  status: Status,
  file: String,
  code: Int?,
): Status {
  val mp3 = directory.resolve(file)
  val probe = probe(mp3)
  save(directory.resolve("${file.substringBeforeLast('.')}.probe.json"), probe)
  val duration = round3(mapper.readTree(probe.ifBlank { "{}" }).at("/format/duration").asDouble(0.0))
  val digest = sha256(mp3)
  val parts = status.parts.map { if (it.file == file) it.copy(durationSeconds = duration, sha256 = digest, exitCode = code) else it }
  return store(directory, status.copy(parts = parts)).also { event(directory, "$file ended after $duration s, exit ${code ?: "none"}") }
}

private fun probe(mp3: Path): String {
  val ffprobe =
    ProcessBuilder(tool("ffprobe"), "-v", "error", "-show_format", "-show_streams", "-of", "json", mp3.toString())
      .redirectError(Redirect.DISCARD)
      .start()
  return ffprobe.inputReader(StandardCharsets.UTF_8).readText().also { ffprobe.waitFor() }
}

private fun extra(reach: Int): Int = if (reach > 0) SEGMENT * (reach + 1) else 0

private fun covered(parts: List<Part>): Double = parts.maxOfOrNull { it.offsetSeconds + (it.durationSeconds ?: 0.0) } ?: 0.0

internal fun length(
  status: Status,
  remaining: Double,
  reach: Int,
): Double = if (!live(status.source) || status.parts.isEmpty() && reach == 0) status.seconds else remaining

private fun live(source: String): Boolean = source.substringBefore('?').endsWith(".m3u8")

private fun stems(): Sequence<String> = generateSequence(1) { it + 1 }.map { if (it == 1) "raw" else "raw-part-${"$it".padStart(3, '0')}" }

private fun store(
  directory: Path,
  status: Status,
): Status = status.also { write(directory.resolve(STATUS), it) }

private fun event(
  directory: Path,
  message: String,
) {
  val stamp = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  Files.writeString(directory.resolve("events.log"), "$stamp $message\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
}
