package ee.tenman.radio

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualWithErrorTolerance
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class StationTest {
  @Test
  fun `serves the recording as live hls`(
    @TempDir tmp: Path,
  ) {
    val response = replay(tmp).use { get(it.url) }
    expect(
      Triple(
        response.headers().firstValue("Content-Type").orElse(""),
        "#EXT-X-TARGETDURATION:6" in response.body(),
        "seg_00000.ts" in response.body(),
      ),
    ).toEqual(Triple("application/vnd.apple.mpegurl", true, true))
  }

  @Test
  fun `serves segments as mpeg-ts`(
    @TempDir tmp: Path,
  ) {
    val type =
      replay(tmp, 2.0).use {
        get(it.url)
        get(it.url.replace("stream.m3u8", "seg_00000.ts")).headers().firstValue("Content-Type").orElse("")
      }
    expect(type).toEqual("video/mp2t")
  }

  @Test
  fun `answers a head of the playlist without starting the replay`(
    @TempDir tmp: Path,
  ) {
    val status =
      replay(tmp, 2.0).use { station ->
        HttpClient.newHttpClient().use {
          it.send(HttpRequest.newBuilder(URI(station.url)).HEAD().build(), HttpResponse.BodyHandlers.discarding()).statusCode()
        }
      }
    expect(status).toEqual(404)
  }

  @Test
  fun `starts part way with from`(
    @TempDir tmp: Path,
  ) {
    Files.createDirectory(tmp.resolve("hls"))
    Station.open(tone(tmp.resolve("in.mp3"), 13.0), tmp.resolve("hls"), port = 0, from = 6.0, speed = 4.0, grace = 100.milliseconds).use {
      get(it.url)
      it.awaitOffAir(40.seconds)
    }
    val listed =
      Files.readAllLines(tmp.resolve("hls/stream.m3u8")).filter { it.startsWith("#EXTINF:") }.sumOf {
        it.removePrefix("#EXTINF:").substringBefore(",").toDouble()
      }
    expect(listed).toEqualWithErrorTolerance(7.0, 0.1)
  }

  @Test
  fun `goes off air after the replay ends`(
    @TempDir tmp: Path,
  ) {
    Files.createDirectory(tmp.resolve("hls"))
    val off =
      Station.open(tone(tmp.resolve("in.mp3"), 2.0), tmp.resolve("hls"), port = 0, speed = 4.0, grace = 100.milliseconds).use {
        get(it.url)
        it.awaitOffAir(40.seconds)
      }
    expect(off).toEqual(true)
  }

  @Test
  fun `listens on the given host`(
    @TempDir tmp: Path,
  ) {
    Files.createDirectory(tmp.resolve("hls"))
    val any =
      Station.open(tone(tmp.resolve("in.mp3"), 2.0), tmp.resolve("hls"), host = "0.0.0.0", port = 0).use {
        it.address.address.isAnyLocalAddress
      }
    expect(any).toEqual(true)
  }

  @Test
  fun `reports a port already in use`(
    @TempDir tmp: Path,
  ) {
    Files.createDirectory(tmp.resolve("hls"))
    ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { taken ->
      expect { Station.open(tone(tmp.resolve("in.mp3"), 2.0), tmp.resolve("hls"), port = taken.localPort) }
        .toThrow<BindException>()
        .messageToContain("in use")
    }
  }
}
