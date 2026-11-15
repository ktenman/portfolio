package ee.tenman.portfolio.service.radio

import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

@Service
class RadioShowService {
  fun write(
    show: RadioShow,
    output: OutputStream,
  ) {
    val whole = show.parts.singleOrNull()?.takeIf { it.inpoint == 0.0 }
    if (whole != null) return whole.resource.inputStream.use { it.transferTo(output) }
    val folder = Files.createTempDirectory("radio-show")
    runCatching { join(show, folder, output) }
      .also { folder.toFile().deleteRecursively() }
      .getOrThrow()
  }

  private fun join(
    show: RadioShow,
    folder: Path,
    output: OutputStream,
  ) {
    val list = folder.resolve("parts.txt")
    val entries = show.parts.withIndex().map { (index, part) -> "file '${local(part.resource, folder, index)}'\ninpoint ${part.inpoint}\n" }
    Files.writeString(list, entries.joinToString(""))
    val process = ProcessBuilder(command(list)).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    val exit =
      runCatching { process.inputStream.use { it.transferTo(output) }.let { process.waitFor() } }
        .also { process.destroyForcibly() }
        .getOrThrow()
    check(exit == 0) { "FFmpeg exited with $exit while joining the parts of ${show.file}" }
  }

  private fun local(
    resource: Resource,
    folder: Path,
    index: Int,
  ): Path {
    if (resource.isFile) return resource.file.toPath()
    val copy = folder.resolve("part-$index.mp3")
    resource.inputStream.use { Files.copy(it, copy) }
    return copy
  }

  private fun command(list: Path): List<String> =
    listOf(
      "ffmpeg",
      "-hide_banner",
      "-nostdin",
      "-loglevel",
      "error",
      "-f",
      "concat",
      "-safe",
      "0",
      "-i",
      list.toString(),
      "-map",
      "0:a",
      "-c",
      "copy",
      "-f",
      "mp3",
      "pipe:1",
    )
}
