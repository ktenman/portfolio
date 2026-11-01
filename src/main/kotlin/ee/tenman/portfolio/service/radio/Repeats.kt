package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.RATE
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val APART = 312
private const val GAP = 93
private const val LEAST = 10
private const val TAIL = 31.5
private const val BIAS = 1 shl 30
private const val REACH = 5.0
private const val MARGIN = 1.0
private const val STEP = 800
private const val WIDTH = 1600
private const val SLACK = 12000
private const val QUIET = 30.0
private const val MATCH = 0.8
private const val CEILING = 20_000_000L
private const val DECODE_SECONDS = 60L

private val log = LoggerFactory.getLogger(Repeats::class.java)

class Fingerprinted(
  val directory: Path,
  val parts: List<RecordingPart>,
  val prints: LongArray,
)

internal data class Airing(
  val from: Double,
  val to: Double,
)

internal class Audio(
  private val first: Long,
  private val samples: DoubleArray,
) {
  fun slice(
    start: Int,
    count: Int,
  ): DoubleArray = (start - first).toInt().let { samples.copyOfRange(it, it + count) }
}

internal class Direction(
  val x: Airing,
  val partner: Airing,
  val audio: (Double, Double) -> Audio?,
)

class Repeats internal constructor(
  private val timeline: Timeline,
  private val directions: List<Direction>,
) {
  fun start(
    from: Double,
    to: Double,
  ): Double? =
    directions
      .filter { it.x.from in from - REACH..to + REACH }
      .mapNotNull { confirm(timeline, it, from) }
      .filter { it < to }
      .minOrNull()
}

fun repeats(
  timeline: Timeline,
  name: String,
  prints: LongArray,
  others: List<Fingerprinted>,
): Repeats {
  val self =
    runs(pairs(name) { join(prints, prints, APART, it) }).flatMap { (i, j) ->
      listOf(Direction(i, j, timeline::audio), Direction(j, i, timeline::audio))
    }
  return Repeats(timeline, self + others.flatMap { matches(name, prints, it) })
}

private fun matches(
  name: String,
  prints: LongArray,
  other: Fingerprinted,
): List<Direction> {
  val names = "$name and ${other.directory.fileName}"
  if (other.directory.fileName.toString() < name) {
    return runs(pairs(names) { join(other.prints, prints, Int.MIN_VALUE, it) }).map { (i, j) -> Direction(j, i, other::audio) }
  }
  return runs(pairs(names) { join(prints, other.prints, Int.MIN_VALUE, it) }).map { (i, j) -> Direction(i, j, other::audio) }
}

private inline fun pairs(
  names: String,
  join: (LongArray?) -> Long,
): LongArray {
  val count = join(null)
  if (count > CEILING) return longArrayOf().also { log.warn("Cannot match $names in more than $CEILING fingerprint pairs") }
  return LongArray(count.toInt()).also { join(it) }.apply { sort() }
}

private fun join(
  first: LongArray,
  second: LongArray,
  least: Int,
  out: LongArray?,
): Long {
  var count = 0L
  for (print in first) {
    val hash = print ushr 32
    for (k in lower(second, hash) until lower(second, hash + 1)) {
      val off = second[k].toInt() - print.toInt()
      if (off < least) continue
      out?.set(count.toInt(), pack(print.toInt(), off))
      count++
      if (count > CEILING) return count
    }
  }
  return count
}

private fun lower(
  prints: LongArray,
  hash: Long,
): Int = prints.binarySearch(hash shl 32).let { if (it < 0) -it - 1 else it }

private fun pack(
  earlier: Int,
  off: Int,
): Long {
  val bin = Math.floorDiv(off, 2)
  return ((bin + BIAS).toLong() shl 32) or (earlier.toLong() shl 1) or (off - 2 * bin).toLong()
}

private fun earlier(pair: Long): Int = pair.toInt() ushr 1

private fun later(pair: Long): Int = earlier(pair) + 2 * ((pair ushr 32).toInt() - BIAS) + (pair.toInt() and 1)

private fun runs(pairs: LongArray): List<Pair<Airing, Airing>> {
  val found = mutableListOf<Pair<Airing, Airing>>()
  var start = 0
  for (end in 1..pairs.size) {
    if (end < pairs.size && pairs[end] ushr 32 == pairs[end - 1] ushr 32 && earlier(pairs[end]) - earlier(pairs[end - 1]) <= GAP) continue
    run(pairs, start, end)?.let(found::add)
    start = end
  }
  return found
}

private fun run(
  pairs: LongArray,
  start: Int,
  end: Int,
): Pair<Airing, Airing>? {
  val first = earlier(pairs[start])
  val last = earlier(pairs[end - 1])
  if (end - start < LEAST || last - first < SECOND) return null
  val range = start until end
  return Airing(first / FPS, (last + TAIL) / FPS) to
    Airing(range.minOf { later(pairs[it]) } / FPS, (range.maxOf { later(pairs[it]) } + TAIL) / FPS)
}

private fun confirm(
  timeline: Timeline,
  direction: Direction,
  bound: Double,
): Double? {
  val mid = (direction.x.from + direction.x.to) / 2
  val centre = (mid * RATE).toInt()
  if (centre - STEP <= bound * RATE) return null
  val x = timeline.audio(bound - MARGIN, mid + MARGIN)
  val template = x.slice(centre - RATE / 2, RATE)
  if (deviation(template) < QUIET) return null
  val guess = direction.partner.from - direction.x.from
  return direction.audio(bound - MARGIN + guess, mid + MARGIN + guess)?.let { partner ->
    lag(template, partner, centre, mid + guess)?.let { walk(x, partner, centre - STEP, it, bound) }
  }
}

private fun lag(
  template: DoubleArray,
  partner: Audio,
  centre: Int,
  at: Double,
): Int? {
  val lo = (at * RATE).toInt() - SLACK
  val (r, k) = ncc(template, window(partner.slice(lo, 2 * SLACK)))
  return if (r >= MATCH) lo + k - (centre - RATE / 2) else null
}

private fun walk(
  x: Audio,
  partner: Audio,
  start: Int,
  shift: Int,
  bound: Double,
): Double? =
  generateSequence(start) { it - STEP }
    .takeWhile { it > bound * RATE }
    .firstOrNull { !alike(x.slice(it - STEP, WIDTH), partner.slice(it - STEP + shift, WIDTH)) }
    ?.let { it.toDouble() / RATE }

private fun alike(
  a: DoubleArray,
  b: DoubleArray,
): Boolean {
  val first = deviation(a) < QUIET
  val second = deviation(b) < QUIET
  return if (first || second) first && second else pearson(a, b) >= MATCH
}

private fun deviation(values: DoubleArray): Double {
  val mean = values.average()
  return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
}

private fun Timeline.audio(
  from: Double,
  to: Double,
): Audio {
  val first = sample(from)
  return Audio(first, read(first, (sample(to) - first).toInt(), Double.NaN))
}

private fun Fingerprinted.audio(
  from: Double,
  to: Double,
): Audio? =
  runCatching { read(sample(from), sample(to)) }
    .onFailure { log.warn("Cannot decode ${directory.fileName} between $from and $to s", it) }
    .getOrNull()

private fun Fingerprinted.read(
  first: Long,
  end: Long,
): Audio? {
  val spans = coverage(directory, parts).filter { sample(it.start) < end && first < sample(it.end) && Files.exists(it.audio) }
  if (spans.isEmpty()) return null
  val samples = DoubleArray((end - first).toInt()) { Double.NaN }
  spans.forEach { span ->
    val begin = max(first, sample(span.start))
    val shorts = decode(span, begin, min(end, sample(span.end)) - begin, directory)
    val at = (begin - first).toInt()
    for (k in 0 until min(shorts.limit(), samples.size - at)) samples[at + k] = shorts.get(k).toDouble()
  }
  return Audio(first, samples)
}

private fun decode(
  span: Span,
  begin: Long,
  count: Long,
  recording: Path,
): ShortBuffer {
  val start = begin.toDouble() / RATE - span.offset
  val length = count.toDouble() / RATE
  val command =
    listOf("ffmpeg", "-nostdin", "-v", "error", "-ss", "%.6f".format(Locale.ROOT, start), "-i", "${span.audio}") +
      listOf("-t", "%.6f".format(Locale.ROOT, length), "-ac", "1", "-ar", "$RATE", "-f", "s16le", "pipe:1")
  val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
  val exit = process.onExit().orTimeout(DECODE_SECONDS, TimeUnit.SECONDS)
  exit.whenComplete { _, error ->
    if (error != null) process.destroyForcibly()
  }
  val bytes = process.inputStream.use { it.readBytes() }
  val code = process.waitFor()
  check(!exit.isCompletedExceptionally) {
    "FFmpeg did not decode ${span.audio.fileName} of ${recording.fileName} from $start s within $DECODE_SECONDS s"
  }
  check(code == 0) { "FFmpeg could not decode ${span.audio.fileName} of ${recording.fileName} from $start s" }
  return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
}
