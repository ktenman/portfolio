package ee.tenman.portfolio.service.radio

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.OutputStream

@Service
class RadioLiveService(
  private val radioProperties: RadioProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun stream(output: OutputStream) {
    val process = ProcessBuilder(command()).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    runCatching { process.inputStream.use { it.transferTo(output) } }
      .onSuccess { log.warn("Live radio stream ${radioProperties.live} ended with FFmpeg exit ${process.waitFor()}") }
      .onFailure { log.info("Live radio listener left: ${it.message}") }
    process.destroyForcibly().waitFor()
  }

  private fun command(): List<String> =
    listOf(
      "ffmpeg",
      "-hide_banner",
      "-nostdin",
      "-loglevel",
      "error",
      "-rw_timeout",
      "15000000",
      "-i",
      radioProperties.live,
      "-map",
      "0:a:0",
      "-c:a",
      "copy",
      "-f",
      "mp3",
      "pipe:1",
    )
}
