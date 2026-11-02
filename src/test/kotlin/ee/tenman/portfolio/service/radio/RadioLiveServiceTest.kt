package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.notToThrow
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Station
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

class RadioLiveServiceTest {
  @TempDir
  lateinit var directory: Path

  private fun station(seconds: Int): RadioLiveService {
    val playlist = directory.resolve("stream.m3u8").toString()
    ffmpeg(
      "-f",
      "lavfi",
      "-i",
      "sine=frequency=440:sample_rate=48000:duration=$seconds",
      "-c:a",
      "libmp3lame",
      "-b:a",
      "256k",
      "-f",
      "hls",
      "-hls_time",
      "2",
      "-hls_list_size",
      "0",
      playlist,
    )
    return RadioLiveService(RadioProperties(recordings = directory, live = playlist))
  }

  @Test
  fun `should pass the station on as MP3 from start to end`() {
    val heard = directory.resolve("kuulatud.mp3")
    Files.newOutputStream(heard).use { station(6).stream(it, Station.KUKU) }
    val format = probe(heard)["format"]
    expect(format["format_name"].asString() to (format["duration"].asString().toDouble() > 5.5)).toEqual("mp3" to true)
  }

  @Test
  @Timeout(30)
  fun `should stop when the listener leaves`() {
    val left =
      object : OutputStream() {
        override fun write(b: Int) = throw IOException("Kuulaja lahkus")
      }
    val live = station(600)
    expect { live.stream(left, Station.KUKU) }.notToThrow()
  }

  @Test
  fun `should pass on the plain MP3 stream of Äripäev`() {
    val stream = directory.resolve("aripaev.mp3")
    ffmpeg("-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100:duration=6", "-c:a", "libmp3lame", "-b:a", "128k", stream.toString())
    val heard = directory.resolve("kuulatud.mp3")
    val live = RadioLiveService(RadioProperties(recordings = directory, live = "puudub.m3u8", aripaevLive = stream.toString()))
    Files.newOutputStream(heard).use { live.stream(it, Station.ARIPAEV) }
    expect(probe(heard)["format"]["duration"].asString().toDouble() > 5.5).toEqual(true)
  }
}
