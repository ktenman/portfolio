package ee.tenman.portfolio.service.radio

import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.TimeUnit

@Service
class RadioRenderService {
  fun render(
    timeline: Timeline,
    keep: List<Pair<Double, Double>>,
    marks: List<Pair<Double, String>>,
    title: String,
    target: Path,
  ) {
    val found = pieces(timeline, keep)
    val chapters = Files.createTempFile(target.parent, "chapters", ".txt")
    val log = Files.createTempFile(target.parent, "ffmpeg", ".log")
    runCatching {
      Files.writeString(chapters, metadata(marks, found.sumOf { it.end - it.start }))
      run(command(timeline, found, chapters, title, target), log)
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
    return listOf("ffmpeg", "-hide_banner", "-nostdin", "-loglevel", "error", "-y") + inputs +
      listOf("-i", chapters.toString(), "-filter_complex", graph, "-map", "[out]", "-map_metadata", count, "-map_chapters", count) +
      listOf("-c:a", "aac", "-b:a", "96k", "-movflags", "+faststart") +
      listOf("-metadata", "title=$title", "-metadata", "artist=Kuku", target.toString())
  }

  private fun plain(seconds: Double): String = seconds.toBigDecimal().toPlainString()

  private fun run(
    command: List<String>,
    log: Path,
  ) {
    val process = ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(log.toFile()).start()
    if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
      process.destroyForcibly()
      throw IllegalStateException("FFmpeg did not finish within $TIMEOUT_MINUTES minutes")
    }
    val tail = Files.readAllLines(log).takeLast(20).joinToString("\n")
    check(process.exitValue() == 0) { "FFmpeg exited with ${process.exitValue()}: $tail" }
  }

  companion object {
    private const val TIMEOUT_MINUTES = 10L
  }
}
