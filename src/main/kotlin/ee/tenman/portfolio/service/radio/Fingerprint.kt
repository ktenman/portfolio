package ee.tenman.portfolio.service.radio

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

const val FINGERPRINTS = "fingerprints.bin"
private const val VERSION = 1
private const val SIZE = 512
private const val STEP = 256
private const val LOWEST = 8
private const val BINS = 128
private const val CHUNK = 250
private const val TEN_MINUTES = 600 * RATE / STEP
private const val CONTEXT = 20
private const val BAND = 12
private const val PERCENTILE = 0.6
const val SECOND = RATE / STEP
const val FPS = 1.0 * RATE / STEP
private const val LOUDEST = 24
private const val AHEAD = 47
private const val FAN = 6
private const val ZONE = 63
private const val SPREAD = 31

private val hann = DoubleArray(SIZE) { 0.5 - 0.5 * cos(2 * PI * it / (SIZE - 1)) }

private data class Peak(
  val frame: Int,
  val bin: Int,
  val value: Float,
)

fun fingerprint(timeline: Timeline): LongArray {
  val samples = sample(timeline.seconds)
  val frames = if (samples < SIZE) 0 else ((samples - SIZE) / STEP + 1).toInt()
  val found = (0 until frames step TEN_MINUTES).flatMap { peaks(timeline, it, min(it + TEN_MINUTES, frames), frames) }
  return pairs(loudest(found))
}

fun writeFingerprints(
  directory: Path,
  prints: LongArray,
) {
  val temporary = directory.resolve("$FINGERPRINTS.tmp")
  val bytes = ByteBuffer.allocate(Int.SIZE_BYTES + prints.size * Long.SIZE_BYTES).putInt(VERSION)
  bytes.asLongBuffer().put(prints)
  Files.write(temporary, bytes.array())
  Files.move(temporary, directory.resolve(FINGERPRINTS), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}

fun readFingerprints(directory: Path): LongArray? {
  val path = directory.resolve(FINGERPRINTS).takeIf(Files::exists) ?: return null
  val bytes = ByteBuffer.wrap(Files.readAllBytes(path))
  if (bytes.getInt() != VERSION) return null
  return LongArray(bytes.remaining() / Long.SIZE_BYTES).also { bytes.asLongBuffer().get(it) }
}

private fun peaks(
  timeline: Timeline,
  start: Int,
  end: Int,
  frames: Int,
): List<Peak> {
  val first = max(start - CONTEXT, 0)
  val rows = min(end + CONTEXT, frames) - first
  val values = spectrogram(timeline, first, rows)
  val threshold = percentile(values, (start - first) * BINS, (end - first) * BINS)
  return (start - first until end - first).flatMap { row ->
    (0 until BINS)
      .filter { values[row * BINS + it] > threshold && greatest(values, rows, row, it) }
      .map { Peak(first + row, it, values[row * BINS + it]) }
  }
}

private fun spectrogram(
  timeline: Timeline,
  first: Int,
  rows: Int,
): FloatArray {
  val values = FloatArray(rows * BINS)
  for (chunk in 0 until rows step CHUNK) {
    val count = min(CHUNK, rows - chunk)
    val samples = timeline.read((first + chunk).toLong() * STEP, (count - 1) * STEP + SIZE)
    for (k in 0 until count) levels(samples, k * STEP).copyInto(values, (chunk + k) * BINS)
  }
  return values
}

private fun levels(
  samples: DoubleArray,
  offset: Int,
): FloatArray {
  val re = DoubleArray(SIZE) { samples[offset + it] * hann[it] }
  val im = DoubleArray(SIZE)
  fft(re, im)
  return FloatArray(BINS) {
    val bin = LOWEST + it
    (20 * log10(sqrt(re[bin] * re[bin] + im[bin] * im[bin]) + 1e-3)).toFloat()
  }
}

private fun percentile(
  values: FloatArray,
  from: Int,
  to: Int,
): Double {
  val block = values.copyOfRange(from, to)
  val position = PERCENTILE * (block.size - 1)
  val index = position.toInt()
  val below = select(block, index)
  val above = (index + 1 until block.size).minOf { block[it] }
  return below + ((position - index) * (above - below))
}

private fun select(
  values: FloatArray,
  rank: Int,
): Float {
  var low = 0
  var high = values.size - 1
  while (low < high) {
    val pivot = values[rank]
    var i = low
    var j = high
    while (i <= j) {
      while (values[i] < pivot) i++
      while (pivot < values[j]) j--
      if (i <= j) {
        values[i] = values[j].also { values[j] = values[i] }
        i++
        j--
      }
    }
    if (j < rank) low = i
    if (rank < i) high = j
  }
  return values[rank]
}

private fun greatest(
  values: FloatArray,
  rows: Int,
  row: Int,
  bin: Int,
): Boolean {
  val value = values[row * BINS + bin]
  for (r in max(row - CONTEXT, 0)..min(row + CONTEXT, rows - 1)) {
    for (b in max(bin - BAND, 0)..min(bin + BAND, BINS - 1)) {
      if (values[r * BINS + b] > value) return false
    }
  }
  return true
}

private fun loudest(peaks: List<Peak>): List<Peak> =
  peaks.groupBy { it.frame / SECOND }.values.flatMap { group ->
    group.sortedByDescending { it.value }.take(LOUDEST).sortedWith(compareBy({ it.frame }, { it.bin }))
  }

private fun pairs(peaks: List<Peak>): LongArray =
  peaks.indices
    .flatMap { i -> partners(peaks, i).map { landmark(peaks[i], it) } }
    .toLongArray()
    .apply { sort() }

private fun partners(
  peaks: List<Peak>,
  index: Int,
): List<Peak> {
  val anchor = peaks[index]
  return peaks
    .subList(index + 1, min(index + 1 + AHEAD, peaks.size))
    .takeWhile { it.frame - anchor.frame <= ZONE }
    .filter { it.frame > anchor.frame && abs(it.bin - anchor.bin) <= SPREAD }
    .take(FAN)
}

private fun landmark(
  anchor: Peak,
  partner: Peak,
): Long {
  val hash = (anchor.bin shl 12) or ((partner.bin - anchor.bin + SPREAD) shl 6) or (partner.frame - anchor.frame)
  return (hash.toLong() shl 32) or anchor.frame.toLong()
}
