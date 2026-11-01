package ee.tenman.radio

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

private const val CHECK_SECONDS = 60.0

data class Schedule(
  val plan: String = "plan.json",
  val heartbeat: String = "heartbeat.json",
)

val SCHEDULES = mapOf("ARIPAEV" to Schedule("plan-aripaev.json", "heartbeat-aripaev.json"))

fun planned(
  recordings: Path,
  record: (Path, Double, Double, List<String>, Slot?) -> RecordingState,
  clock: () -> Double,
  sleep: (Double) -> Boolean,
  schedule: Schedule = Schedule(),
): Int {
  val (capture, now) = await(recordings, schedule, clock, sleep, null) ?: return 130
  val directory = recordings.resolve(capture.name)
  val begin = max(capture.start.toDouble(), now - LOOKBACK)
  beat(recordings.resolve(schedule.heartbeat), now, capture.end.toDouble())
  println("Recording $directory")
  runCatching { record(directory, begin, capture.end - begin, capture.programmes, capture.slot) }
    .onSuccess { println("${it.name.lowercase()}: $directory") }
    .onFailure { println("Recording $directory failed: $it") }
  val stopped = generateSequence { capture.end - clock() }.takeWhile { it > 0 }.any(sleep)
  return if (stopped) 130 else 0
}

private tailrec fun await(
  recordings: Path,
  schedule: Schedule,
  clock: () -> Double,
  sleep: (Double) -> Boolean,
  announced: Capture?,
): Pair<Capture, Double>? {
  val now = clock()
  val capture = upcoming(recordings, recordings.resolve(schedule.plan), now)
  if (capture != null && capture != announced) {
    println("Next recording ${capture.name} from ${shown(capture.start)} until ${shown(capture.end)}")
  }
  if (capture != null && capture.start <= now) return Pair(capture, now)
  val wait = capture?.let { min(CHECK_SECONDS, it.start - now) } ?: CHECK_SECONDS
  beat(recordings.resolve(schedule.heartbeat), now, now + wait)
  return if (sleep(wait)) null else await(recordings, schedule, clock, sleep, capture ?: announced)
}

private fun upcoming(
  recordings: Path,
  plan: Path,
  now: Double,
): Capture? =
  plan(plan)
    .filter { it.end > now || it.end > now - LOOKBACK && unfinished(recordings.resolve(it.name)) }
    .minByOrNull { it.start }

private fun plan(path: Path): List<Capture> =
  runCatching { read<List<Capture>>(path) }.getOrElse { error ->
    if (error !is NoSuchFileException) unreadable(path, error)
    emptyList()
  }

private fun unfinished(directory: Path): Boolean {
  val path = directory.resolve("status.json")
  if (Files.notExists(path)) return false
  return runCatching { read<Status>(path).state == RecordingState.RECORDING }.getOrElse { error ->
    unreadable(path, error)
    false
  }
}

private fun unreadable(
  path: Path,
  error: Throwable,
) {
  System.err.println("Cannot read $path: ${"$error".lines().joinToString(" ")}")
}

private fun beat(
  path: Path,
  seen: Double,
  until: Double,
) {
  write(path, Heartbeat(seen, until))
}

private fun shown(moment: Long): String =
  Instant.ofEpochSecond(moment).atZone(TALLINN).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
