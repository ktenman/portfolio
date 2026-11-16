package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Station
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sqrt

class RadioRenderServiceTest {
  @TempDir
  lateinit var directory: Path

  private val service = RadioRenderService()

  private fun duration(path: Path): Double = probe(path)["format"]["duration"].asString().toDouble()

  @Test
  fun `should join the kept spans with chapters`() {
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, taped(directory, "raw", 30))))
    val keep = listOf(2.0 to 10.0, 15.0 to 25.0)
    val episode = directory.resolve("episode.m4a")
    service.render(
      timeline(directory).spans,
      keep,
      chapters(keep, listOf(OutlineChapter(2.0, "Õhtu; uudised = #1")), PROGRAMME),
      "Keskpäevatund 17.10.2026",
      episode,
      Station.KUKU,
    )
    val found = probe(episode)
    expect((abs(duration(episode) - 18.0) <= 0.1) to found["chapters"].toList().map { it["tags"]["title"].asString() })
      .toEqual(true to listOf("Õhtu; uudised = #1", "Cut 1 (0:08) – Õhtu; uudised = #1"))
  }

  @Test
  fun `should name the station of the recording as the artist`() {
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, taped(directory, "raw", 10))))
    val episode = directory.resolve("episode.m4a")
    service.render(timeline(directory).spans, listOf(2.0 to 8.0), listOf(0.0 to "Uudised"), "Kuum tool", episode, Station.ARIPAEV)
    expect(probe(episode)["format"]["tags"]["artist"].asString()).toEqual("Äripäev")
  }

  @Test
  fun `should take each piece from the part that covers it`() {
    val parts =
      listOf(
        FixturePart("raw.mp3", 0.0, taped(directory, "raw", 10)),
        FixturePart("raw-part-002.mp3", 8.0, taped(directory, "raw-part-002", 10)),
      )
    writeRecording(directory, parts, "partial")
    val episode = directory.resolve("episode.m4a")
    service.render(timeline(directory).spans, listOf(5.0 to 15.0), listOf(0.0 to "Uudised"), "Keskpäevatund", episode, Station.KUKU)
    expect(abs(duration(episode) - 10.0) <= 0.1).toEqual(true)
  }

  @Test
  fun `should play each part from its own offset`() {
    val parts =
      listOf(
        FixturePart("raw.mp3", 0.0, taped(directory, "raw", 10)),
        FixturePart("raw-part-002.mp3", 8.0, silent(directory, "raw-part-002", 10)),
      )
    writeRecording(directory, parts, "partial")
    val episode = directory.resolve("episode.m4a")
    service.render(timeline(directory).spans, listOf(5.0 to 15.0), listOf(0.0 to "Uudised"), "Keskpäevatund", episode, Station.KUKU)
    val audio = decoded(episode)
    val loud = { start: Double, end: Double ->
      sqrt(((start * RATE).toInt() until (end * RATE).toInt()).map { audio[it].toDouble() * audio[it] }.average())
    }
    expect((loud(0.5, 4.5) > 1000) to (loud(5.5, 9.5) < 10)).toEqual(true to true)
  }

  @Test
  fun `should skip a piece too short to fade`() {
    val parts =
      listOf(
        FixturePart("raw.mp3", 0.0, taped(directory, "raw", 10)),
        FixturePart("raw-part-002.mp3", 10.0, taped(directory, "raw-part-002", 10)),
      )
    writeRecording(directory, parts, "partial")
    val episode = directory.resolve("episode.m4a")
    service.render(timeline(directory).spans, listOf(5.0 to 10.005), listOf(0.0 to "Uudised"), "Keskpäevatund", episode, Station.KUKU)
    expect(abs(duration(episode) - 5.0) <= 0.05).toEqual(true)
  }

  @Test
  fun `should report how far FFmpeg has got`() {
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, taped(directory, "raw", 30))))
    val shares = mutableListOf<Double>()
    val episode = directory.resolve("episode.m4a")
    service.render(timeline(directory).spans, listOf(2.0 to 28.0), listOf(0.0 to "Uudised"), "Keskpäevatund", episode, Station.KUKU) {
      shares += it
    }
    expect(shares.zipWithNext().all { (a, b) -> a <= b } to (shares.last() >= 0.9)).toEqual(true to true)
  }

  @Test
  fun `should bring a loud and a quiet show to the same loudness`() {
    val episodes =
      listOf("vali" to 0.4, "vaikne" to 0.1).map { (name, amplitude) ->
        val folder = Files.createDirectories(directory.resolve(name))
        writeRecording(folder, listOf(FixturePart("raw.mp3", 0.0, taped(folder, "raw", 20, "$amplitude*sin(2*PI*1000*t)"))))
        val episode = folder.resolve("episode.m4a")
        service.render(timeline(folder).spans, listOf(2.0 to 18.0), listOf(0.0 to "Uudised"), "Keskpäevatund", episode, Station.KUKU)
        episode
      }
    expect(episodes.map { (abs(level(it).first + 14) <= 0.5) to probe(it)["streams"][0]["sample_rate"].asString() })
      .toEqual(listOf(true to "48000", true to "48000"))
  }

  @Test
  fun `should turn a quiet show up only until its peaks reach the ceiling`() {
    val wave = "(0.02+0.28*lt(mod(t,2),0.1)*0.5*(1-cos(2*PI*t/0.1)))*sin(2*PI*1000*t)"
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, taped(directory, "raw", 20, wave))))
    val episode = directory.resolve("episode.m4a")
    service.render(timeline(directory).spans, listOf(2.0 to 18.0), listOf(0.0 to "Uudised"), "Keskpäevatund", episode, Station.KUKU)
    val (loudness, peak) = level(episode)
    expect((abs(peak + 1.5) <= 0.5) to (loudness < -16)).toEqual(true to true)
  }

  @Test
  fun `should leave a cut too quiet to measure at its level`() {
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, taped(directory, "raw", 10, "0.0001*sin(2*PI*1000*t)"))))
    val episode = directory.resolve("episode.m4a")
    service.render(timeline(directory).spans, listOf(2.0 to 8.0), listOf(0.0 to "Uudised"), "Keskpäevatund", episode, Station.KUKU)
    expect(decoded(episode).maxOf { abs(it.toInt()) }).toBeLessThan(100)
  }

  @Test
  fun `should not turn up a recording whose format changes partway`() {
    expect(level(switched(0.4, 0.05)).second).toBeLessThan(-1.0)
  }

  @Test
  fun `should bring a quiet recording whose format changes partway to the target loudness`() {
    expect(abs(level(switched(0.05, 0.0707)).first + 14) <= 0.5).toEqual(true)
  }

  @Test
  fun `should copy a clip out of an episode within a frame of the asked bounds`() {
    val clip = directory.resolve("klipp.m4a")
    service.clip(episode("saade", 30), 10.0, 20.0, clip)
    expect(abs(duration(clip) - 10.0) <= 0.05).toEqual(true)
  }

  @Test
  fun `should bring clips from a loud and a quiet episode to the same loudness`() {
    val clips =
      listOf("vali" to 0.4, "vaikne" to 0.05).map { (stem, amplitude) ->
        directory.resolve("$stem-klipp.m4a").also { service.clip(episode(stem, 20, amplitude), 2.0, 18.0, it) }
      }
    expect(clips.map { abs(level(it).first + 14) <= 0.5 }).toEqual(listOf(true, true))
  }

  @Test
  fun `should join clips from two episodes with a chapter per story`() {
    val first = directory.resolve("esimene-klipp.m4a")
    val second = directory.resolve("teine-klipp.m4a")
    service.clip(episode("esimene", 30), 5.0, 15.0, first)
    service.clip(episode("teine", 20), 2.0, 10.0, second)
    val unused = directory.resolve("puudub.pcm")
    val spans = listOf(Span(0.0, 10.0, unused, 0.0, first), Span(10.0, 18.0, unused, 10.0, second))
    val digest = directory.resolve("nadal.m4a")
    service.render(spans, listOf(0.0 to 10.0, 10.0 to 18.0), listOf(0.0 to "Ust-Luga", 10.0 to "Telia"), "Nädal", digest, Station.KUKU)
    val titles = probe(digest)["chapters"].toList().map { it["tags"]["title"].asString() }
    expect((abs(duration(digest) - 18.0) <= 0.1) to titles).toEqual(true to listOf("Ust-Luga", "Telia"))
  }

  private fun episode(
    stem: String,
    seconds: Int,
    amplitude: Double = 0.3,
  ): Path {
    val path = directory.resolve("$stem.m4a")
    ffmpeg("-f", "lavfi", "-i", "aevalsrc='$amplitude*sin(2*PI*440*t)':s=48000:d=$seconds", "-c:a", "aac", "-b:a", "96k", "$path")
    return path
  }

  private fun switched(
    first: Double,
    second: Double,
  ): Path {
    val teine = directory.resolve("teine.mp3")
    taped(directory, "esimene", 10, "$first*sin(2*PI*1000*t)")
    ffmpeg("-f", "lavfi", "-i", "aevalsrc='$second*sin(2*PI*1000*t)':s=44100:d=10", "-c:a", "libmp3lame", "$teine")
    Files.write(directory.resolve("raw.mp3"), Files.readAllBytes(directory.resolve("esimene.mp3")) + Files.readAllBytes(teine))
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, ShortArray(20 * RATE))))
    val episode = directory.resolve("episode.m4a")
    service.render(timeline(directory).spans, listOf(1.0 to 19.0), listOf(0.0 to "Uudised"), "Keskpäevatund", episode, Station.KUKU)
    return episode
  }

  private fun level(path: Path): Pair<Double, Double> {
    val command = listOf("ffmpeg", "-hide_banner", "-nostats", "-i", "$path", "-af", "ebur128=peak=true:framelog=quiet", "-f", "null", "-")
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.use { it.readBytes().decodeToString() }
    check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { "ffmpeg failed to measure $path" }
    val summary = output.substringAfterLast("Summary:")
    val read = { label: String -> checkNotNull(Regex("""\s$label:\s+(-?[\d.]+)""").find(summary)) { output }.groupValues[1].toDouble() }
    return read("I") to read("Peak")
  }
}
