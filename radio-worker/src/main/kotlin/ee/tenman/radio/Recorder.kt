package ee.tenman.radio

import java.lang.ProcessBuilder.Redirect
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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
private const val BEHIND = 3
private const val REMUX_MINUTES = 10L
private val RAW = listOf("mp3", "aac", "m4a")
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
  val file = "$stem.${status.schedule.audio}"
  if (status.parts.any { stem(it.file) == stem } || Files.notExists(directory.resolve(file))) return status
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
  schedule: Schedule,
): Int {
  if (!live(source)) return 0
  return (away / schedule.segment).roundToInt().coerceIn(0, (schedule.lookback / schedule.segment).toInt())
}

internal fun command(
  source: String,
  seconds: Double,
  reach: Int,
  base: Path,
  schedule: Schedule,
): List<String> =
  listOf(tool("ffmpeg"), "-hide_banner", "-nostdin", "-nostats", "-loglevel", "info", "-n") +
    (if (source.startsWith("http://") || source.startsWith("https://")) listOf("-headers", "Accept-Encoding: gzip\r\n") else emptyList()) +
    listOf("-rw_timeout", "15000000") +
    (if (live(source)) listOf("-m3u8_hold_counters", "10") else emptyList()) +
    (if (reach > 0) listOf("-live_start_index", "${-(BEHIND + reach)}") else emptyList()) +
    listOf("-t", "%.3f".format(Locale.ROOT, seconds + extra(reach, schedule.segment)), "-i", source) +
    listOf("-map", "0:a:0", "-c:a", "copy", "-flush_packets", "1", "$base.${schedule.audio}") +
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
    .takeWhile { taken(directory, it) }
    .fold(status) { current, stem -> register(directory, current, stem) }

private fun taken(
  directory: Path,
  name: String,
): Boolean = RAW.any { Files.exists(directory.resolve("$name.$it")) }

private fun complete(
  directory: Path,
  status: Status,
): Status {
  status.parts.filter { it.file.endsWith(".m4a") }.forEach { Files.deleteIfExists(directory.resolve("${stem(it.file)}.aac")) }
  return status.parts.filter { it.durationSeconds == null }.fold(status) { current, part -> finish(directory, current, part.file, null) }
}

private tailrec fun capture(
  directory: Path,
  status: Status,
  stop: CountDownLatch,
): Status {
  val remaining = round3(status.endsAt - now())
  val reach = reach(status.source, away(directory, status), status.schedule)
  val margin = extra(reach, status.schedule.segment)
  val tail = round(max(0.0, status.seconds - covered(status.parts)))
  if (stop.count == 0L || remaining + margin <= 0 || remaining <= 0 && tail == 0.0) return status
  val attempted = attempt(directory, trim(directory, status), remaining, reach, stop)
  val part = attempted.parts.drop(status.parts.size).firstOrNull()
  if (remaining <= 0 || part != null && (part.durationSeconds ?: 0.0) >= remaining + margin - 1) return attempted
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
  val written = Files.getLastModifiedTime(directory.resolve(newest.file)).toMillis() / 1000.0
  return max(behind + BEHIND * status.schedule.segment, clock - written)
}

private fun trim(
  directory: Path,
  status: Status,
): Status {
  val earliest = round3(now() - status.schedule.lookback)
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
  val stem = stems().first { !taken(directory, it) }
  val ffmpeg =
    ProcessBuilder(command(status.source, length(status, remaining, reach), reach, directory.resolve(stem), status.schedule))
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
  val file = "$stem.${status.schedule.audio}"
  val registered = register(directory, followed, stem)
  if (registered.parts.any { it.file == file }) return finish(directory, registered, file, ffmpeg.exitValue())
  check(Files.notExists(directory.resolve(file))) { "FFmpeg wrote $file but $stem.log does not say where the stream starts" }
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
  val kept = if (file.endsWith(".aac")) remux(directory, file) else file
  val audio = directory.resolve(kept)
  val probe = probe(audio)
  save(directory.resolve("${stem(kept)}.probe.json"), probe)
  val duration = round3(mapper.readTree(probe.ifBlank { "{}" }).at("/format/duration").asDouble(0.0))
  val digest = sha256(audio)
  val parts =
    status.parts.map { if (it.file == file) it.copy(file = kept, durationSeconds = duration, sha256 = digest, exitCode = code) else it }
  val stored = store(directory, status.copy(parts = parts))
  if (kept != file) Files.deleteIfExists(directory.resolve(file))
  return stored.also { event(directory, "$kept ended after $duration s, exit ${code ?: "none"}") }
}

private fun remux(
  directory: Path,
  file: String,
): String {
  val target = "${stem(file)}.m4a"
  if (Files.exists(directory.resolve(target))) return target
  if (Files.size(directory.resolve(file)) == 0L) return file
  val partial = directory.resolve("$target.tmp")
  Files.deleteIfExists(partial)
  val command =
    listOf(tool("ffmpeg"), "-hide_banner", "-nostdin", "-loglevel", "error", "-n", "-i", "${directory.resolve(file)}") +
      listOf("-map", "0:a:0", "-c:a", "copy", "-movflags", "+faststart", "-f", "mp4", "$partial")
  val log = "${stem(file)}.log"
  val ffmpeg =
    ProcessBuilder(command)
      .redirectErrorStream(true)
      .redirectOutput(Redirect.appendTo(directory.resolve(log).toFile()))
      .start()
  if (!ffmpeg.waitFor(REMUX_MINUTES, TimeUnit.MINUTES)) ffmpeg.destroyForcibly()
  check(ffmpeg.waitFor() == 0) { "FFmpeg could not copy $file into $target, see $log" }
  Files.move(partial, directory.resolve(target), StandardCopyOption.ATOMIC_MOVE)
  return target
}

private fun probe(audio: Path): String {
  val ffprobe =
    ProcessBuilder(tool("ffprobe"), "-v", "error", "-show_format", "-show_streams", "-of", "json", audio.toString())
      .redirectError(Redirect.DISCARD)
      .start()
  return ffprobe.inputReader(StandardCharsets.UTF_8).readText().also { ffprobe.waitFor() }
}

private fun extra(
  reach: Int,
  segment: Double,
): Double = if (reach > 0) segment * (reach + 1) else 0.0

internal val Status.schedule: Schedule
  get() = station?.let(SCHEDULES::get) ?: Schedule()

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
