package ee.tenman.radio

import com.k2fsa.sherpa.onnx.AudioTagging
import com.k2fsa.sherpa.onnx.AudioTaggingConfig
import com.k2fsa.sherpa.onnx.AudioTaggingModelConfig
import java.lang.ProcessBuilder.Redirect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path

private const val SPEECH = 0
private const val MUSIC = 137
private const val CLASSES = 527
private const val WINDOW = 15600
private const val STRIDE = RATE / 2
private const val THREADS = 1

data class Heard(
  val pcm: Path,
  val origin: Double,
  val skip: Double,
)

fun frames(
  heard: List<Heard>,
  tagger: Path,
): List<SoundFrame> {
  val tagging = AudioTagging(config(tagger))
  return try {
    heard.flatMap { scores(tagging, it) }
  } finally {
    tagging.release()
  }
}

fun listen(
  directory: Path,
  parts: List<Part>,
  tagger: Path,
) {
  val heard = parts.mapIndexed { index, part -> Heard(pcm(directory, part), part.offsetSeconds, overlap(directory, parts, index)) }
  write(directory.resolve("sounds.json"), Sounds("ced-tiny int8", frames(heard, tagger)))
}

fun hear(
  directory: Path,
  tagger: Path,
) {
  val parts = read<Status>(directory.resolve("status.json")).parts
  val decoded = parts.filterNot { Files.exists(pcm(directory, it)) }.map { decode(directory, it) }
  try {
    listen(directory, parts, tagger)
  } finally {
    decoded.forEach(Files::deleteIfExists)
  }
}

private fun config(tagger: Path): AudioTaggingConfig {
  val model =
    AudioTaggingModelConfig
      .builder()
      .setCED(tagger.resolve("model.int8.onnx").toString())
      .setNumThreads(THREADS)
      .setDebug(false)
      .build()
  return AudioTaggingConfig
    .builder()
    .setModel(model)
    .setLabels(tagger.resolve("class_labels_indices.csv").toString())
    .build()
}

private fun scores(
  tagging: AudioTagging,
  heard: Heard,
): List<SoundFrame> {
  val starts = Math.rint(heard.skip * RATE).toLong()..samples(heard.pcm) - WINDOW step STRIDE.toLong()
  if (starts.isEmpty()) return emptyList()
  return FileChannel.open(heard.pcm).use { channel -> starts.map { frame(tagging, window(channel, it), heard.origin, it) } }
}

private fun window(
  channel: FileChannel,
  first: Long,
): FloatArray {
  val bytes = ByteBuffer.allocate(2 * WINDOW).order(ByteOrder.LITTLE_ENDIAN)
  channel.read(bytes, 2 * first)
  return FloatArray(WINDOW) { bytes.getShort(2 * it) / 32768f }
}

private fun frame(
  tagging: AudioTagging,
  audio: FloatArray,
  origin: Double,
  first: Long,
): SoundFrame {
  val stream = tagging.createStream()
  stream.acceptWaveform(audio, RATE)
  val events = tagging.compute(stream, CLASSES).associate { it.index to it.prob.toDouble() }
  stream.release()
  return SoundFrame(round3(origin + (first + WINDOW / 2.0) / RATE), round3(events.getValue(SPEECH)), round3(events.getValue(MUSIC)))
}

private fun decode(
  directory: Path,
  part: Part,
): Path {
  val source = directory.resolve(part.file)
  val target = pcm(directory, part)
  val command = listOf("ffmpeg", "-n", "-v", "error", "-i", source.toString(), "-ac", "1", "-ar", "$RATE", "-f", "s16le", target.toString())
  val process = ProcessBuilder(command).redirectOutput(Redirect.INHERIT).redirectError(Redirect.INHERIT).start()
  check(process.waitFor() == 0) { "FFmpeg could not decode $source, exit ${process.exitValue()}" }
  return target
}
