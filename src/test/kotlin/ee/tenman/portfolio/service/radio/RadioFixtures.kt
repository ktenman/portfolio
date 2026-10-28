package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.domain.RadioAiring
import ee.tenman.portfolio.domain.RadioProgramme
import ee.tenman.portfolio.repository.RadioAiringRepository
import ee.tenman.portfolio.repository.RadioProgrammeRepository
import io.mockk.every
import io.mockk.mockk
import tools.jackson.databind.JsonNode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

data class FixturePart(
  val file: String,
  val offset: Double,
  val samples: ShortArray,
)

fun noise(
  seconds: Double,
  silences: List<Double> = emptyList(),
  seed: Int = 7,
  gap: Double = 0.3,
): ShortArray {
  val random = Random(seed)
  val samples = ShortArray((seconds * RATE).toInt()) { random.nextInt(-8000, 8000).toShort() }
  silences.forEach { start -> samples.fill(0, (start * RATE).toInt(), ((start + gap) * RATE).toInt()) }
  return samples
}

fun melody(): ShortArray {
  val bed = noise(4.0, seed = 13)
  return ShortArray(bed.size) { k ->
    val t = (k % 4000) / 16000.0
    val tone = (0..2).sumOf { v -> sin(2 * PI * (300.0 + ((k / 4000 * 3 + v) * 1013) % 3700) * t) }
    (7500 * tone * exp(-t / 0.1) + 0.45 * bed[k]).toInt().toShort()
  }
}

fun mixed(
  a: ShortArray,
  b: ShortArray,
): ShortArray = ShortArray(a.size) { (a[it] + b[it]).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }

fun ShortArray.aired(
  at: Double,
  audio: ShortArray = melody(),
): ShortArray = also { audio.copyInto(it, sample(at).toInt()) }

fun encoded(
  directory: Path,
  stem: String,
  samples: ShortArray,
): ShortArray {
  val raw = directory.resolve("$stem.raw")
  val mp3 = directory.resolve("$stem.mp3")
  Files.write(raw, bytes(samples))
  ffmpeg("-f", "s16le", "-ar", "$RATE", "-ac", "1", "-i", "$raw", "-ar", "48000", "-ac", "2", "-c:a", "libmp3lame", "-b:a", "256k", "$mp3")
  Files.delete(raw)
  return decoded(mp3)
}

private fun bytes(samples: ShortArray): ByteArray =
  ByteBuffer
    .allocate(samples.size * 2)
    .order(ByteOrder.LITTLE_ENDIAN)
    .apply { asShortBuffer().put(samples) }
    .array()

fun heard(
  from: Double,
  to: Double,
  speech: Double,
  music: Double,
): List<SoundFrame> =
  generateSequence(from) { it + 0.5 }
    .takeWhile { it < to }
    .map { SoundFrame(it, speech, music) }
    .toList()

fun constant(
  seconds: Double,
  value: Int,
): ShortArray = ShortArray((seconds * RATE).toInt()) { value.toShort() }

fun writeRecording(
  directory: Path,
  parts: List<FixturePart>,
  state: String = "captured",
) {
  parts.forEach { part -> Files.write(directory.resolve(part.file.substringBeforeLast(".") + ".pcm"), bytes(part.samples)) }
  val listed = parts.map { mapOf("file" to it.file, "offsetSeconds" to it.offset, "sha256" to "0".repeat(64)) }
  val status = mapOf("state" to state, "startedAt" to 0.0, "seconds" to 0.0, "parts" to listed)
  Files.writeString(directory.resolve("status.json"), JsonMapperFactory.instance.writeValueAsString(status))
}

fun airings(vararg found: RadioAiring): RadioAiringRepository = mockk { every { findOverlapping(any(), any()) } returns found.toList() }

fun programmes(vararg found: RadioProgramme): RadioProgrammeRepository = mockk { every { findAllById(any()) } returns found.toList() }

fun timeline(directory: Path): Timeline = Timeline(directory, readStatus(directory).parts)

fun editList(
  keep: List<KeptSpan> = listOf(KeptSpan(0.0, 4200.0)),
  boundaries: List<Boundary> = emptyList(),
  topics: List<EpisodeChapter> = emptyList(),
  hosts: List<String> = emptyList(),
  music: List<KeptSpan> = emptyList(),
) = EditList(
  keep = keep,
  durationSeconds = 4200.0,
  parts = listOf(RecordingPart("raw.mp3", 0.0)),
  boundaries = boundaries,
  hosts = hosts,
  outline = OutlineSummary(null, 1, 1, 1),
  chapters = emptyList(),
  reviewed = false,
  renderedAt = "2026-10-17T10:20:00Z",
  topics = topics,
  music = music,
)

fun ffmpeg(vararg arguments: String): ByteArray {
  val process = ProcessBuilder(listOf("ffmpeg", "-v", "error", "-y") + arguments).redirectError(ProcessBuilder.Redirect.DISCARD).start()
  val output = process.inputStream.use { it.readBytes() }
  check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { "ffmpeg failed for ${arguments.toList()}" }
  return output
}

fun pcm(bytes: ByteArray): ShortArray {
  val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
  return ShortArray(buffer.remaining()).also { buffer.get(it) }
}

fun taped(
  directory: Path,
  stem: String,
  seconds: Int,
): ShortArray {
  val wave = "if(between(mod(t,10),7,7.4),0,0.3*sin(2*PI*440*t))"
  val mp3 = directory.resolve("$stem.mp3").toString()
  ffmpeg("-f", "lavfi", "-i", "aevalsrc='$wave|$wave':s=48000:d=$seconds", "-c:a", "libmp3lame", "-b:a", "256k", mp3)
  return pcm(ffmpeg("-i", mp3, "-ac", "1", "-ar", "$RATE", "-f", "s16le", "pipe:1"))
}

fun silent(
  directory: Path,
  stem: String,
  seconds: Int,
): ShortArray {
  ffmpeg("-f", "lavfi", "-i", "anullsrc=r=44100:cl=mono:d=$seconds", "-c:a", "libmp3lame", directory.resolve("$stem.mp3").toString())
  return ShortArray(seconds * RATE)
}

fun decoded(path: Path): ShortArray = pcm(ffmpeg("-i", path.toString(), "-ac", "1", "-ar", "$RATE", "-f", "s16le", "pipe:1"))

fun probe(path: Path): JsonNode {
  val process = ProcessBuilder("ffprobe", "-v", "error", "-show_format", "-show_chapters", "-of", "json", path.toString()).start()
  val output = process.inputStream.use { it.readBytes() }
  check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { "ffprobe failed for $path" }
  return JsonMapperFactory.instance.readTree(output)
}
