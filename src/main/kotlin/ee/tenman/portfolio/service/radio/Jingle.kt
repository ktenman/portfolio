package ee.tenman.portfolio.service.radio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private const val MATCH = 0.8
private const val ALIKE = 0.9
private const val WINDOW = 10.0

class Spectrum(
  val re: DoubleArray,
  val im: DoubleArray,
)

class SharedJingle(
  val cuts: List<Double?>,
  val r: Double,
)

private class Window(
  val samples: DoubleArray,
  val spectrum: Spectrum,
  val size: Int,
)

fun fft(
  re: DoubleArray,
  im: DoubleArray,
  inverse: Boolean = false,
) {
  val n = re.size
  var j = 0
  for (i in 1 until n) {
    var bit = n shr 1
    while (j and bit != 0) {
      j = j xor bit
      bit = bit shr 1
    }
    j = j xor bit
    if (i < j) {
      re[i] = re[j].also { re[j] = re[i] }
      im[i] = im[j].also { im[j] = im[i] }
    }
  }
  var length = 2
  while (length <= n) {
    butterflies(re, im, length, if (inverse) 2 * PI / length else -2 * PI / length)
    length = length shl 1
  }
  if (inverse) re.indices.forEach { re[it] /= n.toDouble() }
}

private fun butterflies(
  re: DoubleArray,
  im: DoubleArray,
  length: Int,
  angle: Double,
) {
  val half = length / 2
  val wr = DoubleArray(half) { cos(angle * it) }
  val wi = DoubleArray(half) { sin(angle * it) }
  for (i in re.indices step length) {
    for (k in 0 until half) {
      val a = i + k
      val b = a + half
      val xr = re[b] * wr[k] - im[b] * wi[k]
      val xi = re[b] * wi[k] + im[b] * wr[k]
      re[b] = re[a] - xr
      im[b] = im[a] - xi
      re[a] += xr
      im[a] += xi
    }
  }
}

private fun spectrum(
  values: DoubleArray,
  size: Int,
): Spectrum {
  val re = values.copyOf(size)
  val im = DoubleArray(size)
  fft(re, im)
  return Spectrum(re, im)
}

private fun cumulative(values: DoubleArray): DoubleArray = values.runningFold(0.0) { sum, value -> sum + value }.toDoubleArray()

fun ncc(
  template: DoubleArray,
  signal: DoubleArray,
  spectrum: Spectrum,
  size: Int,
): Pair<Double, Int> {
  val mean = template.average()
  val centred = DoubleArray(template.size) { template[it] - mean }
  val own = spectrum(centred, size)
  val re = DoubleArray(size) { spectrum.re[it] * own.re[it] + spectrum.im[it] * own.im[it] }
  val im = DoubleArray(size) { spectrum.im[it] * own.re[it] - spectrum.re[it] * own.im[it] }
  fft(re, im, inverse = true)
  val n = template.size
  val sums = cumulative(signal)
  val squares = cumulative(DoubleArray(signal.size) { signal[it] * signal[it] })
  val energy = centred.sumOf { it * it }
  val r =
    DoubleArray(signal.size - n + 1) {
      val variance = (squares[it + n] - squares[it]) - (sums[it + n] - sums[it]) * (sums[it + n] - sums[it]) / n
      re[it] / sqrt(max(variance, 1e-9) * energy)
    }
  val best = r.indices.filterNot { r[it].isNaN() }.maxByOrNull { r[it] } ?: return Double.NaN to 0
  return r[best] to best
}

private fun window(
  timeline: Timeline,
  back: Double,
): Window {
  val samples = timeline.samples(back - WINDOW, back)
  val size = Integer.highestOneBit(samples.size + RATE) shl 1
  return Window(samples, spectrum(samples, size), size)
}

private fun pearson(
  a: DoubleArray,
  b: DoubleArray,
): Double {
  val ma = a.average()
  val mb = b.average()
  val cov = a.indices.sumOf { (a[it] - ma) * (b[it] - mb) }
  return cov / sqrt(a.sumOf { (it - ma) * (it - ma) } * b.sumOf { (it - mb) * (it - mb) })
}

private fun similar(
  timeline: Timeline,
  first: Double,
  second: Double,
): Double = pearson(timeline.read(sample(first), RATE / 10), timeline.read(sample(second), RATE / 10))

private fun onset(
  timeline: Timeline,
  t1: Double,
  t2: Double,
): Double? =
  (0 until 130)
    .map { -0.3 + it * 0.01 }
    .firstOrNull { d -> (0 until 3).all { k -> similar(timeline, t1 + d + k * 0.02, t2 + d + k * 0.02) >= ALIKE } }

fun jingle(
  timeline: Timeline,
  db: DoubleArray,
  returns: List<Double>,
): SharedJingle? {
  if (returns.size < 2) return null
  val first = timeline.samples(returns[0] - WINDOW, returns[0])
  val second = window(timeline, returns[1])
  return (0 until first.size - RATE step RATE / 50).asSequence().firstNotNullOfOrNull { offset ->
    val (r, lag) = ncc(first.copyOfRange(offset, offset + RATE), second.samples, second.spectrum, second.size)
    if (!(r >= MATCH)) return@firstNotNullOfOrNull null
    val t1 = returns[0] - WINDOW + offset.toDouble() / RATE
    val t2 = returns[1] - WINDOW + lag.toDouble() / RATE
    onset(timeline, t1, t2)?.let { start -> shared(timeline, db, returns, t1 + start, t2 + start, r) }
  }
}

private fun shared(
  timeline: Timeline,
  db: DoubleArray,
  returns: List<Double>,
  first: Double,
  second: Double,
  r: Double,
): SharedJingle {
  val template = timeline.read(sample(first), RATE)
  val later =
    returns.drop(2).map { back ->
      val found = window(timeline, back)
      val (rr, lag) = ncc(template, found.samples, found.spectrum, found.size)
      val t = back - WINDOW + lag.toDouble() / RATE
      if (rr >= MATCH) valley(db, t - 0.6, t + 0.05) else null
    }
  return SharedJingle(listOf(valley(db, first - 0.6, first + 0.05), valley(db, second - 0.6, second + 0.05)) + later, r)
}
