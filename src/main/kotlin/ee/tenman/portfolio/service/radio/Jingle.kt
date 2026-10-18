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

class Window(
  val samples: DoubleArray,
  val spectrum: Spectrum,
) {
  val size: Int = spectrum.re.size
  val sums: DoubleArray = cumulative(samples) { it }
  val squares: DoubleArray = cumulative(samples) { it * it }
}

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
  val turn = if (inverse) 2 * PI / n else -2 * PI / n
  val wr = DoubleArray(n / 2) { cos(turn * it) }
  val wi = DoubleArray(n / 2) { sin(turn * it) }
  generateSequence(2) { it shl 1 }.takeWhile { it <= n }.forEach { butterflies(re, im, it, wr, wi) }
  if (inverse) re.indices.forEach { re[it] /= n.toDouble() }
}

private fun butterflies(
  re: DoubleArray,
  im: DoubleArray,
  length: Int,
  wr: DoubleArray,
  wi: DoubleArray,
) {
  val half = length / 2
  val stride = re.size / length
  for (i in re.indices step length) {
    for (k in 0 until half) {
      val a = i + k
      val b = a + half
      val w = k * stride
      val xr = re[b] * wr[w] - im[b] * wi[w]
      val xi = re[b] * wi[w] + im[b] * wr[w]
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

private fun cumulative(
  values: DoubleArray,
  term: (Double) -> Double,
): DoubleArray {
  val out = DoubleArray(values.size + 1)
  values.indices.forEach { out[it + 1] = out[it] + term(values[it]) }
  return out
}

fun ncc(
  template: DoubleArray,
  window: Window,
): Pair<Double, Int> {
  val mean = template.average()
  val centred = DoubleArray(template.size) { template[it] - mean }
  val own = spectrum(centred, window.size)
  val re = own.re
  val im = own.im
  val signal = window.spectrum
  for (k in 0 until window.size) {
    val r = signal.re[k] * re[k] + signal.im[k] * im[k]
    im[k] = signal.im[k] * re[k] - signal.re[k] * im[k]
    re[k] = r
  }
  fft(re, im, inverse = true)
  return best(re, window, template.size, centred.sumOf { it * it })
}

private fun best(
  num: DoubleArray,
  window: Window,
  n: Int,
  energy: Double,
): Pair<Double, Int> {
  val sums = window.sums
  val squares = window.squares
  var found = Double.NaN to 0
  for (k in 0..window.samples.size - n) {
    val sum = sums[k + n] - sums[k]
    val r = num[k] / sqrt(max(squares[k + n] - squares[k] - sum * sum / n, 1e-9) * energy)
    if (!r.isNaN() && (found.first.isNaN() || r > found.first)) found = r to k
  }
  return found
}

private fun window(
  timeline: Timeline,
  back: Double,
): Window {
  val samples = timeline.samples(back - WINDOW, back)
  val size = Integer.highestOneBit(samples.size + RATE) shl 1
  return Window(samples, spectrum(samples, size))
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
    val (r, lag) = ncc(first.copyOfRange(offset, offset + RATE), second)
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
      val (rr, lag) = ncc(template, window(timeline, back))
      val t = back - WINDOW + lag.toDouble() / RATE
      if (rr >= MATCH) valley(db, t - 0.6, t + 0.05) else null
    }
  return SharedJingle(listOf(valley(db, first - 0.6, first + 0.05), valley(db, second - 0.6, second + 0.05)) + later, r)
}
