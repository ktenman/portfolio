package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
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
      timeline(directory),
      keep,
      chapters(keep, listOf(OutlineChapter(2.0, "Õhtu; uudised = #1")), PROGRAMME),
      "Keskpäevatund 17.10.2026",
      episode,
    )
    val found = probe(episode)
    expect((abs(duration(episode) - 18.0) <= 0.1) to found["chapters"].toList().map { it["tags"]["title"].asString() })
      .toEqual(true to listOf("Õhtu; uudised = #1", "Cut 1 (0:08) – Õhtu; uudised = #1"))
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
    service.render(timeline(directory), listOf(5.0 to 15.0), listOf(0.0 to "Uudised"), "Keskpäevatund", episode)
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
    service.render(timeline(directory), listOf(5.0 to 15.0), listOf(0.0 to "Uudised"), "Keskpäevatund", episode)
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
    service.render(timeline(directory), listOf(5.0 to 10.005), listOf(0.0 to "Uudised"), "Keskpäevatund", episode)
    expect(abs(duration(episode) - 5.0) <= 0.05).toEqual(true)
  }

  @Test
  fun `should report how far FFmpeg has got`() {
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, taped(directory, "raw", 30))))
    val shares = mutableListOf<Double>()
    service.render(timeline(directory), listOf(2.0 to 28.0), listOf(0.0 to "Uudised"), "Keskpäevatund", directory.resolve("episode.m4a")) {
      shares += it
    }
    expect(shares.zipWithNext().all { (a, b) -> a <= b } to (shares.last() >= 0.9)).toEqual(true to true)
  }
}
