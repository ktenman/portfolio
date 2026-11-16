package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.Station
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
    spans: List<Span>,
    keep: List<Pair<Double, Double>>,
    marks: List<Pair<Double, String>>,
    title: String,
    target: Path,
    station: Station,
    progress: (Double) -> Unit = {},
  ) {
    val found = pieces(spans, keep)
    val length = found.sumOf { it.end - it.start }
    val chapters = Files.createTempFile(target.parent, "chapters", ".txt")
    val log = Files.createTempFile(target.parent, "ffmpeg", ".log")
    runCatching {
      Files.writeString(chapters, metadata(marks, length))
      val graph = graph(found)
      val share = { seconds: Double -> min(1.0, seconds / length) }
      run(measure(spans, graph), log) { progress(MEASURE_SHARE * share(it)) }
      val volume = "$graph,volume=${decibels(log)}dB[level]"
      run(encode(spans, volume, chapters, title, target, station), log) { progress(MEASURE_SHARE + (1 - MEASURE_SHARE) * share(it)) }
    }.also {
      Files.deleteIfExists(chapters)
      Files.deleteIfExists(log)
    }.getOrThrow()
  }

  private fun graph(found: List<Piece>): String {
    val trims =
      found.mapIndexed { n, piece ->
        val length = piece.end - piece.start
        "[${piece.index}:a]atrim=start=${plain(piece.start - piece.offset)}:end=${plain(piece.end - piece.offset)},asetpts=PTS-STARTPTS," +
          "afade=t=in:d=$FADE,afade=t=out:st=${String.format(Locale.ROOT, "%.3f", length - FADE)}:d=$FADE[p$n]"
      }
    return trims.joinToString(";") + ";" + found.indices.joinToString("") { "[p$it]" } + "concat=n=${found.size}:v=0:a=1"
  }

  fun clip(
    source: Path,
    start: Double,
    end: Double,
    target: Path,
  ) {
    val log = Files.createTempFile(target.parent, "ffmpeg", ".log")
    val input = FFMPEG + listOf("-ss", plain(start), "-t", plain(end - start), "-i", source.toString())
    runCatching {
      run(input + listOf("-af", LOUDNESS, "-f", "null", "-"), log) {}
      val encode = listOf("-loglevel", "error", "-y", "-af", "volume=${decibels(log)}dB", "-c:a", "aac", "-b:a", CLIP_BITRATE)
      run(input + encode + target.toString(), log) {}
    }.also { Files.deleteIfExists(log) }.getOrThrow()
  }

  fun silence(
    target: Path,
    seconds: Double,
  ) {
    val log = Files.createTempFile(target.parent, "ffmpeg", ".log")
    val encode = listOf("-loglevel", "error", "-y", "-f", "lavfi", "-i", SILENCE, "-t", plain(seconds), "-c:a", "aac", "-b:a", CLIP_BITRATE)
    runCatching { run(FFMPEG + encode + target.toString(), log) {} }.also { Files.deleteIfExists(log) }.getOrThrow()
  }

  private fun ffmpeg(
    spans: List<Span>,
    vararg flags: String,
  ): List<String> = FFMPEG + listOf("-progress", "pipe:1", *flags) + spans.flatMap { listOf("-i", it.audio.toString()) }

  private fun measure(
    spans: List<Span>,
    graph: String,
  ): List<String> = ffmpeg(spans) + listOf("-filter_complex", "$graph,$LOUDNESS[level]", "-map", "[level]", "-f", "null", "-")

  private fun decibels(log: Path): String = String.format(Locale.ROOT, "%.2f", gain(Files.readString(log)))

  private fun gain(log: String): Double {
    val summaries = log.split("Summary:").drop(1)
    check(summaries.isNotEmpty()) { "FFmpeg printed no loudness summary: ${tail(log.lines())}" }
    val measured = summaries.filter { reading(it, "I") > SILENCE_LUFS }
    if (measured.isEmpty()) return 0.0
    return min(TARGET_LUFS - measured.maxOf { reading(it, "I") }, CEILING_DBTP - measured.maxOf { reading(it, "Peak") })
  }

  private fun reading(
    summary: String,
    label: String,
  ): Double {
    val found = checkNotNull(Regex("""\s$label:\s+(\S+)""").find(summary)) { "FFmpeg printed no $label in its loudness summary: $summary" }
    return found.groupValues[1].toDouble()
  }

  private fun encode(
    spans: List<Span>,
    graph: String,
    chapters: Path,
    title: String,
    target: Path,
    station: Station,
  ): List<String> {
    val count = spans.size.toString()
    return ffmpeg(spans, "-loglevel", "error", "-y") +
      listOf("-i", chapters.toString(), "-filter_complex", graph, "-map", "[level]", "-map_metadata", count, "-map_chapters", count) +
      listOf("-c:a", "aac", "-b:a", "96k", "-movflags", "+faststart") +
      listOf("-metadata", "title=$title", "-metadata", "artist=${station.label}", target.toString())
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
    check(process.exitValue() == 0) { "FFmpeg exited with ${process.exitValue()}: ${tail(Files.readAllLines(log))}" }
  }

  private fun tail(lines: List<String>): String = lines.takeLast(20).joinToString("\n")

  private fun written(line: String): Double? = line.substringAfter("out_time_us=", "").toLongOrNull()?.let { it / 1_000_000.0 }

  companion object {
    private const val TIMEOUT_MINUTES = 10L
    private const val TARGET_LUFS = -14.0
    private const val CEILING_DBTP = -1.5
    private const val SILENCE_LUFS = -70.0
    private const val MEASURE_SHARE = 0.25
    private const val CLIP_BITRATE = "192k"
    private const val LOUDNESS = "ebur128=peak=true:framelog=quiet"
    private const val SILENCE = "anullsrc=r=48000:cl=stereo"
    private val FFMPEG = listOf("ffmpeg", "-hide_banner", "-nostdin", "-nostats")
  }
}
