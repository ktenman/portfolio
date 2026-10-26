package ee.tenman.radio

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val PLAYLIST = "stream.m3u8"
private val SEGMENT = Regex("""seg_\d{5}\.ts""")

class Station private constructor(
  private val server: HttpServer,
  private val hls: Path,
  private val command: List<String>,
  private val grace: Duration,
) : AutoCloseable {
  val address: InetSocketAddress = server.address
  private val offAir = CountDownLatch(1)
  private var ffmpeg: Process? = null
  private var closed = false

  fun awaitOffAir(timeout: Duration): Boolean = offAir.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)

  override fun close() {
    val replay =
      synchronized(this) {
        closed = true
        ffmpeg
      }
    replay?.destroy()
    replay?.waitFor()
    server.stop(1)
  }

  private fun serve(exchange: HttpExchange) {
    exchange.use {
      val name = it.requestURI.path.removePrefix("/")
      if (name == PLAYLIST && it.requestMethod == "GET") onAir()
      val type = type(name)
      if (type == null || !Files.isRegularFile(hls.resolve(name))) {
        it.sendResponseHeaders(404, -1)
        return
      }
      val body = Files.readAllBytes(hls.resolve(name))
      it.responseHeaders.set("Content-Type", type)
      it.sendResponseHeaders(200, body.size.toLong())
      it.responseBody.write(body)
    }
  }

  private fun onAir() {
    val replay = start() ?: return
    while (Files.notExists(hls.resolve(PLAYLIST)) && replay.isAlive) Thread.sleep(100)
  }

  @Synchronized
  private fun start(): Process? {
    if (closed) return null
    return ffmpeg ?: ProcessBuilder(command).inheritIO().start().also { process ->
      ffmpeg = process
      Thread.ofVirtual().start { finish(process) }
    }
  }

  private fun finish(replay: Process) {
    replay.waitFor()
    Thread.sleep(grace.inWholeMilliseconds)
    server.stop(1)
    offAir.countDown()
  }

  companion object {
    fun open(
      recording: Path,
      hls: Path,
      host: String = "127.0.0.1",
      port: Int = 8770,
      from: Double = 0.0,
      speed: Double = 1.0,
      grace: Duration = 12.seconds,
    ): Station {
      val server = HttpServer.create(InetSocketAddress(host, port), 0)
      val station = Station(server, hls, command(recording, hls, from, speed), grace)
      server.executor = Executors.newVirtualThreadPerTaskExecutor()
      server.createContext("/", station::serve)
      server.start()
      return station
    }
  }
}

private fun type(name: String): String? =
  when {
    name == PLAYLIST -> "application/vnd.apple.mpegurl"
    SEGMENT.matches(name) -> "video/mp2t"
    else -> null
  }

private fun command(
  recording: Path,
  hls: Path,
  from: Double,
  speed: Double,
): List<String> =
  listOf(
    "ffmpeg",
    "-hide_banner",
    "-nostdin",
    "-loglevel",
    "error",
    "-readrate",
    speed.toString(),
    "-ss",
    from.toString(),
    "-i",
    recording.toString(),
    "-map",
    "0:a:0",
    "-c:a",
    "copy",
    "-f",
    "hls",
    "-hls_time",
    "6",
    "-hls_list_size",
    "0",
    "-hls_flags",
    "omit_endlist",
    "-hls_segment_filename",
    hls.resolve("seg_%05d.ts").toString(),
    hls.resolve(PLAYLIST).toString(),
  )
