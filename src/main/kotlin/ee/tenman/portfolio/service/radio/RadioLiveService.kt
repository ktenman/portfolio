package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.Station
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.OutputStream

@Service
class RadioLiveService(
  private val radioProperties: RadioProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun stream(
    output: OutputStream,
    station: Station,
  ) {
    val url = url(station)
    val process = ProcessBuilder(command(url)).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    runCatching { process.inputStream.use { it.transferTo(output) } }
      .onSuccess { log.warn("Live radio stream $url ended with FFmpeg exit ${process.waitFor()}") }
      .onFailure { log.info("Live radio listener left: ${it.message}") }
    process.destroyForcibly().waitFor()
  }

  private fun url(station: Station): String =
    when (station) {
      Station.KUKU -> radioProperties.live
      Station.ARIPAEV -> radioProperties.aripaevLive
    }

  private fun command(url: String): List<String> =
    listOf(
      "ffmpeg",
      "-hide_banner",
      "-nostdin",
      "-loglevel",
      "error",
      "-rw_timeout",
      "15000000",
      "-i",
      url,
      "-map",
      "0:a:0",
      "-c:a",
      "copy",
      "-f",
      "mp3",
      "pipe:1",
    )
}
