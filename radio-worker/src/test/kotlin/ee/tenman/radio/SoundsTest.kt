package ee.tenman.radio

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SoundsTest {
  private val tagger = Path.of("ced-tiny")

  @Test
  fun `an arpeggio is heard as music`(
    @TempDir tmp: Path,
  ) {
    val found = frames(listOf(Heard(arpeggio(tmp.resolve("a.pcm"), 8.0), 0.0, 0.0)), tagger)
    expect(found.count { it.music >= 0.5 && it.speech < 0.3 } * 2).toBeGreaterThan(found.size)
  }

  @Test
  fun `silence is heard as neither speech nor music`(
    @TempDir tmp: Path,
  ) {
    val found = frames(listOf(Heard(silence(tmp.resolve("s.pcm"), 4.0), 0.0, 0.0)), tagger)
    expect(found.maxOf { maxOf(it.speech, it.music) }).toBeLessThan(0.3)
  }

  @Test
  fun `each frame sits at the middle of its window in recording time`(
    @TempDir tmp: Path,
  ) {
    val found = frames(listOf(Heard(silence(tmp.resolve("s.pcm"), 2.0), 15.0, 0.0)), tagger)
    expect(close(found.map { it.at }, listOf(15.4875, 15.9875, 16.4875))).toEqual(true)
  }

  @Test
  fun `hearing starts after the audio it skips`(
    @TempDir tmp: Path,
  ) {
    val found = frames(listOf(Heard(silence(tmp.resolve("s.pcm"), 10.0), 4.0, 6.0)), tagger)
    expect(close(found.map { it.at }, (0 until 7).map { 10.4875 + it * 0.5 })).toEqual(true)
  }

  @Test
  fun `listen hears each part from the start of the recording`(
    @TempDir tmp: Path,
  ) {
    writeRecording(
      tmp,
      listOf(Written("raw.mp3", 0.0, ShortArray(2 * RATE), true), Written("raw-part-002.mp3", 15.0, ShortArray(2 * RATE), true)),
      RecordingState.PARTIAL,
    )
    listen(tmp, status(tmp).parts, tagger)
    expect(
      close(read<Sounds>(tmp.resolve("sounds.json")).frames.map { it.at }, listOf(0.4875, 0.9875, 1.4875, 15.4875, 15.9875, 16.4875)),
    ).toEqual(true)
  }

  @Test
  fun `listen names the tagger and its revision ahead of the frames`(
    @TempDir tmp: Path,
  ) {
    writeRecording(tmp, listOf(Written("raw.mp3", 0.0, ShortArray(2 * RATE), true)), RecordingState.CAPTURED)
    listen(tmp, status(tmp).parts, tagger)
    expect(head(tmp.resolve("sounds.json"))).toEqual(listOf("model" to "ced-tiny int8", "revision" to "2024-04-19"))
  }

  @Test
  fun `listen hears audio a part repeats from the one before once`(
    @TempDir tmp: Path,
  ) {
    writeRecording(
      tmp,
      listOf(Written("raw.mp3", 0.0, ShortArray(10 * RATE), true), Written("raw-part-002.mp3", 4.0, ShortArray(10 * RATE), true)),
      RecordingState.PARTIAL,
    )
    listen(tmp, status(tmp).parts, tagger)
    val found = read<Sounds>(tmp.resolve("sounds.json")).frames
    expect(close(listOf(found.size.toDouble(), found[19].at), listOf(26.0, 10.4875))).toEqual(true)
  }

  @Test
  fun `hear decodes a recording whose pcm is gone and leaves none behind`(
    @TempDir tmp: Path,
  ) {
    tone(tmp.resolve("raw.mp3"), 4.0)
    write(
      tmp.resolve("status.json"),
      Status(RecordingState.CAPTURED, "http://127.0.0.1:9/stream.m3u8", 0.0, 0.0, 4.0, listOf(listing("raw.mp3", 0.0, 4.0, true))),
    )
    hear(tmp, tagger)
    expect(Pair(read<Sounds>(tmp.resolve("sounds.json")).frames.size, Files.exists(tmp.resolve("raw.pcm")))).toEqual(Pair(7, false))
  }
}
