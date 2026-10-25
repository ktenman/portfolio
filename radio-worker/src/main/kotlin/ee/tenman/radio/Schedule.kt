package ee.tenman.radio

import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

private const val CHECK_SECONDS = 60.0

fun planned(
  recordings: Path,
  record: (Path, Double, Double, List<String>, Slot?) -> RecordingState,
  clock: () -> Double,
  sleep: (Double) -> Boolean,
): Int {
  val (capture, now) = await(recordings, clock, sleep, null) ?: return 130
  val directory = recordings.resolve(capture.name)
  val begin = max(capture.start.toDouble(), now - LOOKBACK)
  beat(recordings, now, capture.end.toDouble())
  println("Recording $directory")
  runCatching { record(directory, begin, capture.end - begin, capture.programmes, capture.slot) }
    .onSuccess { println("$it: $directory") }
    .onFailure { println("Recording $directory failed: $it") }
  val stopped = generateSequence { capture.end - clock() }.takeWhile { it > 0 }.any(sleep)
  return if (stopped) 130 else 0
}

private tailrec fun await(
  recordings: Path,
  clock: () -> Double,
  sleep: (Double) -> Boolean,
  announced: Capture?,
): Pair<Capture, Double>? {
  val now = clock()
  val capture = upcoming(recordings, now)
  if (capture != null && capture != announced) {
    println("Next recording ${capture.name} from ${shown(capture.start)} until ${shown(capture.end)}")
  }
  if (capture != null && capture.start <= now) return Pair(capture, now)
  val wait = capture?.let { min(CHECK_SECONDS, it.start - now) } ?: CHECK_SECONDS
  beat(recordings, now, now + wait)
  return if (sleep(wait)) null else await(recordings, clock, sleep, capture ?: announced)
}

private fun upcoming(
  recordings: Path,
  now: Double,
): Capture? =
  runCatching {
    read<List<Capture>>(recordings.resolve("plan.json")).filter { it.end > now }.minByOrNull { it.start }
  }.getOrNull()

private fun beat(
  recordings: Path,
  seen: Double,
  until: Double,
) {
  write(recordings.resolve("heartbeat.json"), Heartbeat(seen, until))
}

private fun shown(moment: Long): String =
  Instant.ofEpochSecond(moment).atZone(ZoneId.of("Europe/Tallinn")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
