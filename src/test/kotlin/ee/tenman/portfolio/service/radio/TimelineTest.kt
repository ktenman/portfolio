package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualWithErrorTolerance
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.math.round

class TimelineTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun `should decode the pcm of a part again from its mp3`() {
    ffmpeg("-f", "lavfi", "-i", "sine=frequency=440:duration=2", "-c:a", "libmp3lame", directory.resolve("raw.mp3").toString())
    expect(Timeline(directory, listOf(RecordingPart("raw.mp3", 0.0))).seconds).toEqualWithErrorTolerance(2.0, 0.1)
  }

  @Test
  fun `should cover a part without its pcm for the length the worker measured`() {
    expect(coverage(directory, listOf(RecordingPart("raw.mp3", 10.0, durationSeconds = 50.0))).single().end).toEqual(60.0)
  }

  @Test
  fun `should refuse to read a part whose audio is kept only in the bucket`() {
    expect { Timeline(directory, listOf(RecordingPart("raw.mp3", 0.0))) }.toThrow<IllegalStateException>()
  }

  @Test
  fun `should mark a zeroed stretch as digital silence`() {
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, noise(20.0, listOf(11.0)))))
    val gap = silences(loudness(timeline(directory)), 5.0, 15.0).single()
    expect(listOf(gap.first, gap.second).map { round(it * 100) / 100 }).toEqual(listOf(11.0, 11.3))
  }

  @Test
  fun `should read an overlap from the earlier part`() {
    writeRecording(
      directory,
      listOf(FixturePart("raw.mp3", 0.0, constant(10.0, 100)), FixturePart("raw-part-002.mp3", 4.0, constant(10.0, 200))),
    )
    val recording = timeline(directory)
    val samples = recording.samples(9.9, 10.1)
    expect(listOf(recording.seconds, samples.first(), samples.last())).toEqual(listOf(14.0, 100.0, 200.0))
  }

  @Test
  fun `should read silence where no part covers`() {
    writeRecording(
      directory,
      listOf(FixturePart("raw.mp3", 0.0, constant(1.0, 100)), FixturePart("raw-part-002.mp3", 3.0, constant(1.0, 100))),
    )
    expect(timeline(directory).samples(1.5, 2.5).toList()).toEqual(List(RATE) { 0.0 })
  }

  @Test
  fun `should leave loudness undefined where no part covers`() {
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, noise(10.0)), FixturePart("raw-part-002.mp3", 15.0, noise(10.0))))
    val db = loudness(timeline(directory))
    expect((1100 until 1400).all { db[it].isNaN() } to silences(db, 9.0, 16.0)).toEqual(true to emptyList())
  }

  @Test
  fun `should report the level before and after each silence`() {
    val samples = noise(10.0, listOf(5.0))
    (5.3 * RATE).toInt().until(samples.size).forEach { samples[it] = (samples[it] / 10).toShort() }
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, samples)))
    val found = events(loudness(timeline(directory)))
    expect(found.map { it.kind to (it.before - it.after > 15) }).toEqual(listOf("digital silence" to true))
  }

  @Test
  fun `should find a short drop inside loud audio`() {
    val samples = noise(10.0)
    (5 * RATE).until((5.2 * RATE).toInt()).forEach { samples[it] = (samples[it] / 100).toShort() }
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, samples)))
    expect(dips(loudness(timeline(directory)), 3.0, 7.0).single()).toEqualWithErrorTolerance(5.1, 0.1)
  }

  @Test
  fun `should not count a single quiet frame inside loud audio as a dip`() {
    expect(dips(DoubleArray(2000) { -7.0 }.also { it[1000] = -23.0 }, 8.0, 12.0)).toEqual(emptyList())
  }

  @Test
  fun `should count three quiet frames inside loud audio as a dip`() {
    val db = DoubleArray(2000) { -7.0 }.also { it.fill(-23.0, 1000, 1003) }
    expect(dips(db, 8.0, 12.0).single()).toEqualWithErrorTolerance(10.005, 0.001)
  }

  @Test
  fun `should read a part cut off inside a sample`() {
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, constant(1.0, 100))))
    Files.write(directory.resolve("raw.pcm"), byteArrayOf(1), StandardOpenOption.APPEND)
    expect(timeline(directory).samples(0.5, 1.0).toList()).toEqual(List(RATE / 2) { 100.0 })
  }

  @Test
  fun `should measure the frame across a seam that is not frame aligned`() {
    val second = noise(10.0)
    (7.7 * RATE).toInt().until((7.9 * RATE).toInt()).forEach { second[it] = (second[it] / 100).toShort() }
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, noise(10.005)), FixturePart("raw-part-002.mp3", 3.0, second)), "partial")
    val db = loudness(timeline(directory))
    expect(db[1000].isNaN() to dips(db, 9.0, 12.0).map { it in 10.7..10.9 }).toEqual(false to listOf(true))
  }
}
