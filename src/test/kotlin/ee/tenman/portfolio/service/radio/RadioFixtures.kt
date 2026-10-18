package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.configuration.JsonMapperFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

data class FixturePart(
  val file: String,
  val offset: Double,
  val samples: ShortArray,
)

fun noise(
  seconds: Double,
  silences: List<Double> = emptyList(),
): ShortArray {
  val random = Random(7)
  val samples = ShortArray((seconds * RATE).toInt()) { random.nextInt(-8000, 8000).toShort() }
  silences.forEach { start -> samples.fill(0, (start * RATE).toInt(), ((start + 0.3) * RATE).toInt()) }
  return samples
}

fun constant(
  seconds: Double,
  value: Int,
): ShortArray = ShortArray((seconds * RATE).toInt()) { value.toShort() }

fun writeRecording(
  directory: Path,
  parts: List<FixturePart>,
  state: String = "captured",
) {
  parts.forEach { part ->
    val bytes = ByteBuffer.allocate(part.samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
    part.samples.forEach { bytes.putShort(it) }
    Files.write(directory.resolve(part.file.substringBeforeLast(".") + ".pcm"), bytes.array())
  }
  val listed = parts.map { mapOf("file" to it.file, "offsetSeconds" to it.offset, "sha256" to "0".repeat(64)) }
  val status = mapOf("state" to state, "startedAt" to 0.0, "seconds" to 0.0, "parts" to listed)
  Files.writeString(directory.resolve("status.json"), JsonMapperFactory.instance.writeValueAsString(status))
}

fun timeline(directory: Path): Timeline = Timeline(directory, readStatus(directory).parts)
