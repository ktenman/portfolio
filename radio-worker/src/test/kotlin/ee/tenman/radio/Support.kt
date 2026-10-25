package ee.tenman.radio

import java.lang.ProcessBuilder.Redirect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

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
  return render(path, "-f", "lavfi", "-i", "aevalsrc='$wave|$wave':s=48000:d=$seconds", "-c:a", "libmp3lame", "-b:a", "256k")
}

fun status(directory: Path): Status = read(directory.resolve("status.json"))

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
