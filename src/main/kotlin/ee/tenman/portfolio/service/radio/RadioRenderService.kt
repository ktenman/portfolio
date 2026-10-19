package ee.tenman.portfolio.service.radio

import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

@Service
class RadioRenderService {
  fun render(
    timeline: Timeline,
    keep: List<Pair<Double, Double>>,
    marks: List<Pair<Double, String>>,
    title: String,
    target: Path,
    progress: (Double) -> Unit = {},
  ) {
    val found = pieces(timeline.spans, keep)
    val length = found.sumOf { it.end - it.start }
    val chapters = Files.createTempFile(target.parent, "chapters", ".txt")
    val log = Files.createTempFile(target.parent, "ffmpeg", ".log")
    runCatching {
      Files.writeString(chapters, metadata(marks, length))
      run(command(timeline, found, chapters, title, target), log) { progress(min(1.0, it / length)) }
    }.also {
      Files.deleteIfExists(chapters)
      Files.deleteIfExists(log)
    }.getOrThrow()
  }

  private fun command(
    timeline: Timeline,
    found: List<Piece>,
    chapters: Path,
    title: String,
    target: Path,
  ): List<String> {
    val trims =
      found.mapIndexed { n, piece ->
        val length = piece.end - piece.start
        "[${piece.index}:a]atrim=start=${plain(piece.start - piece.offset)}:end=${plain(piece.end - piece.offset)},asetpts=PTS-STARTPTS," +
          "afade=t=in:d=$FADE,afade=t=out:st=${String.format(Locale.ROOT, "%.3f", length - FADE)}:d=$FADE[p$n]"
      }
    val graph = trims.joinToString(";") + ";" + found.indices.joinToString("") { "[p$it]" } + "concat=n=${found.size}:v=0:a=1[out]"
    val inputs = timeline.spans.flatMap { listOf("-i", it.audio.toString()) }
    val count = timeline.spans.size.toString()
    return listOf("ffmpeg", "-hide_banner", "-nostdin", "-loglevel", "error", "-nostats", "-progress", "pipe:1", "-y") + inputs +
      listOf("-i", chapters.toString(), "-filter_complex", graph, "-map", "[out]", "-map_metadata", count, "-map_chapters", count) +
      listOf("-c:a", "aac", "-b:a", "96k", "-movflags", "+faststart") +
      listOf("-metadata", "title=$title", "-metadata", "artist=Kuku", target.toString())
  }

  private fun plain(seconds: Double): String = seconds.toBigDecimal().toPlainString()

  private fun run(
    command: List<String>,
    log: Path,
    seconds: (Double) -> Unit,
  ) {
    val process = ProcessBuilder(command).redirectError(log.toFile()).start()
    val expired = AtomicBoolean()
    process.onExit().orTimeout(TIMEOUT_MINUTES, TimeUnit.MINUTES).whenComplete { _, error ->
      if (error != null && expired.compareAndSet(false, true)) process.destroyForcibly()
    }
    runCatching { process.inputStream.bufferedReader().useLines { lines -> lines.mapNotNull(::written).forEach(seconds) } }
      .onFailure { process.destroyForcibly() }
      .getOrThrow()
    process.waitFor()
    check(!expired.get()) { "FFmpeg did not finish within $TIMEOUT_MINUTES minutes" }
    val tail = Files.readAllLines(log).takeLast(20).joinToString("\n")
    check(process.exitValue() == 0) { "FFmpeg exited with ${process.exitValue()}: $tail" }
  }

  private fun written(line: String): Double? = line.substringAfter("out_time_us=", "").toLongOrNull()?.let { it / 1_000_000.0 }

  companion object {
    private const val TIMEOUT_MINUTES = 10L
  }
}
