package ee.tenman.portfolio.service.radio

import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

const val RATE = 16000
const val HOP = 160
const val FRAME = HOP.toDouble() / RATE
private const val BLOCK = 300
const val DIGITAL = "digital silence"

data class Span(
  val start: Double,
  val end: Double,
  val pcm: Path,
  val offset: Double,
  val audio: Path,
)

data class AudioEvent(
  val start: Double,
  val end: Double,
  val kind: String,
  val before: Double,
  val after: Double,
)

class Timeline(
  directory: Path,
  parts: List<RecordingPart>,
) {
  val spans: List<Span> = coverage(directory, parts)
  val seconds: Double = spans.maxOfOrNull { it.end } ?: 0.0
  private val buffers: List<ShortBuffer> = spans.map { map(it.pcm) }

  fun samples(
    start: Double,
    end: Double,
  ): DoubleArray {
    val first = (start * RATE).toLong()
    return read(first, ((end * RATE).toLong() - first).toInt())
  }

  fun read(
    first: Long,
    count: Int,
  ): DoubleArray {
    val out = DoubleArray(max(count, 0))
    spans.forEachIndexed { index, span ->
      val shift = sample(span.offset)
      for (k in max(first, sample(span.start)) until min(first + count, sample(span.end))) {
        out[(k - first).toInt()] = buffers[index].get((k - shift).toInt()).toDouble()
      }
    }
    return out
  }
}

fun sample(seconds: Double): Long = round(seconds * RATE).toLong()

fun pcmOf(
  directory: Path,
  file: String,
): Path = directory.resolve(file.substringBeforeLast(".") + ".pcm")

private fun pcmSeconds(path: Path): Double = if (Files.exists(path)) Files.size(path) / 2 / RATE.toDouble() else 0.0

private fun map(path: Path): ShortBuffer =
  FileChannel.open(path).use { channel ->
    channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size() / 2 * 2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
  }

fun coverage(
  directory: Path,
  parts: List<RecordingPart>,
): List<Span> =
  parts
    .fold(emptyList<Span>() to 0.0) { (spans, covered), part ->
      val pcm = pcmOf(directory, part.file)
      val start = max(part.offsetSeconds, covered)
      val end = part.offsetSeconds + pcmSeconds(pcm)
      (if (start < end) spans + Span(start, end, pcm, part.offsetSeconds, directory.resolve(part.file)) else spans) to max(covered, end)
    }.first

fun loudness(timeline: Timeline): DoubleArray {
  val db = DoubleArray((timeline.seconds * RATE).toInt() / HOP) { Double.NaN }
  for (frame in db.indices step BLOCK) {
    val count = min(BLOCK, db.size - frame)
    val block = timeline.read(frame.toLong() * HOP, count * HOP)
    for (k in 0 until count) {
      val power = (0 until HOP).sumOf { block[k * HOP + it] * block[k * HOP + it] } / HOP
      db[frame + k] = 20 * log10(max(sqrt(power), 1.0) / 32768)
    }
  }
  return uncover(db, timeline.spans)
}

private fun uncover(
  db: DoubleArray,
  spans: List<Span>,
): DoubleArray {
  val covered = BooleanArray(db.size)
  spans.forEach { span ->
    val first = Math.floorDiv(sample(span.start), HOP.toLong()).toInt()
    val last = min(Math.ceilDiv(sample(span.end), HOP.toLong()).toInt(), db.size)
    covered.fill(true, first, max(first, last))
  }
  db.indices.filterNot { covered[it] }.forEach { db[it] = Double.NaN }
  return db
}

fun runs(mask: BooleanArray): List<Pair<Int, Int>> {
  val edges = (0..mask.size).filter { k -> (k < mask.size && mask[k]) != (k > 0 && mask[k - 1]) }
  return edges.chunked(2).map { it[0] to it[1] }
}

private data class Quiet(
  val start: Int,
  val end: Int,
  val kind: String,
)

private fun quiet(db: DoubleArray): Set<Quiet> {
  val silent = runs(BooleanArray(db.size) { db[it] < -75 })
  val digital = silent.filter { it.second - it.first >= 3 }.map { Quiet(it.first, it.second, DIGITAL) }
  val low = runs(BooleanArray(db.size) { db[it] < -50 }).filter { it.second - it.first >= 30 }.map { Quiet(it.first, it.second, "quiet") }
  return (digital + low).toSet()
}

private fun index(seconds: Double): Int = (seconds / FRAME).toInt()

fun silences(
  db: DoubleArray,
  start: Double,
  end: Double,
): List<Pair<Double, Double>> {
  val i = max(index(start), 0)
  val j = min(index(end), db.size)
  if (i >= j) return emptyList()
  return quiet(db.copyOfRange(i, j))
    .map { (i + it.start) * FRAME to (i + it.end) * FRAME }
    .distinct()
    .sortedWith(compareBy({ it.first }, { it.second }))
}

fun valley(
  db: DoubleArray,
  start: Double,
  end: Double,
): Double {
  val i = max(index(start), 0)
  val found = (i until min(index(end), db.size)).filterNot { db[it].isNaN() }.minByOrNull { db[it] }
  checkNotNull(found) { "No audio between $start and $end s" }
  return found * FRAME + FRAME / 2
}

private fun median(values: DoubleArray): Double {
  if (values.any { it.isNaN() }) return Double.NaN
  val sorted = values.sortedArray()
  val middle = sorted.size / 2
  return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}

fun dips(
  db: DoubleArray,
  start: Double,
  end: Double,
): List<Double> {
  val i = max(index(start), 100)
  val j = min(index(end), db.size - 100)
  if (i >= j) return emptyList()
  val depth = DoubleArray(j - i) { median(db.copyOfRange(i + it - 100, i + it + 101)) - db[i + it] }
  return runs(BooleanArray(depth.size) { depth[it] > 8 })
    .filter { (a, b) -> (a until b).maxOf { depth[it] } > 15 }
    .map { (a, b) -> valley(db, (i + a) * FRAME, (i + b) * FRAME) }
}

private fun level(
  db: DoubleArray,
  start: Int,
  end: Int,
): Double {
  val values = db.copyOfRange(min(max(start, 0), db.size), min(max(end, 1), db.size)).filterNot { it.isNaN() }.toDoubleArray()
  return if (values.isEmpty()) Double.NaN else round(median(values) * 10) / 10
}

fun events(db: DoubleArray): List<AudioEvent> {
  val merged =
    quiet(db)
      .sortedWith(compareBy({ it.start }, { it.end }, { it.kind }))
      .fold(emptyList<Quiet>()) { found, next ->
        val last = found.lastOrNull()
        if (last == null || next.start > last.end) return@fold found + next
        val kind = if (DIGITAL == next.kind || DIGITAL == last.kind) DIGITAL else next.kind
        found.dropLast(1) + Quiet(last.start, max(next.end, last.end), kind)
      }
  return merged.map {
    val before = level(db, it.start - 300, it.start)
    AudioEvent(round(it.start * FRAME * 100) / 100, round(it.end * FRAME * 100) / 100, it.kind, before, level(db, it.end, it.end + 300))
  }
}
