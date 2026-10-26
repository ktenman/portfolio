package ee.tenman.radio

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.notToThrow
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

private val tagger = Path.of("ced-tiny")

private fun transcribed(
  directory: Path,
  ears: Ears = StubEars(60.0),
): Unit = transcribe(directory, { ears }, tagger, { true }, CountDownLatch(1))

private fun captured(
  directory: Path,
  seconds: Double,
) {
  val status = status(directory)
  val parts = status.parts.map { it.copy(durationSeconds = seconds) }
  write(directory.resolve("status.json"), status.copy(state = RecordingState.CAPTURED, parts = parts))
}

class TranscriberTest {
  @Test
  fun `cuts a chunk at each endpoint the recognizer reports`(
    @TempDir tmp: Path,
  ) {
    writeRecording(tmp, listOf(Written("raw.mp3", 0.0, noise(13.0), true)), RecordingState.CAPTURED)
    transcribed(tmp, StubEars(5.0))
    expect(chunks(tmp).map { Triple(it.from, it.to, it.final) })
      .toEqual(listOf(Triple(0.0, 5.0, false), Triple(5.0, 10.0, false), Triple(10.0, 13.0, true)))
  }

  @Test
  fun `times words from the start of the recording`(
    @TempDir tmp: Path,
  ) {
    writeRecording(
      tmp,
      listOf(Written("raw.mp3", 0.0, noise(10.0), true), Written("raw-part-002.mp3", 15.0, noise(10.0), true)),
      RecordingState.PARTIAL,
    )
    transcribed(tmp)
    expect(transcript(tmp).words.map { it.start }).toEqual(listOf(0.0, 15.0))
  }

  @Test
  fun `skips audio a part repeats from the one before`(
    @TempDir tmp: Path,
  ) {
    writeRecording(
      tmp,
      listOf(Written("raw.mp3", 0.0, noise(10.0), true), Written("raw-part-002.mp3", 4.0, noise(10.0), true)),
      RecordingState.PARTIAL,
    )
    val stub = StubEars(60.0)
    transcribed(tmp, stub)
    expect(Pair(stub.heard, transcript(tmp).words.map { it.start })).toEqual(Pair(listOf(10.0, 4.0), listOf(0.0, 10.0)))
  }

  @Test
  fun `resumes after the last finished chunk`(
    @TempDir tmp: Path,
  ) {
    writeRecording(tmp, listOf(Written("raw.mp3", 0.0, noise(13.0), true)), RecordingState.CAPTURED)
    append(tmp.resolve("transcript.jsonl"), Chunk("raw.mp3", 0.0, 5.0, false, emptyList()))
    val stub = StubEars(60.0)
    transcribed(tmp, stub)
    expect(stub.heard).toEqual(listOf(8.0))
  }

  @Test
  fun `drops a last line torn inside a character`(
    @TempDir tmp: Path,
  ) {
    writeRecording(tmp, listOf(Written("raw.mp3", 0.0, noise(13.0), true)), RecordingState.CAPTURED)
    append(tmp.resolve("transcript.jsonl"), Chunk("raw.mp3", 0.0, 5.0, false, emptyList()))
    val torn = """{"part": "raw.mp3", "text": "lõ""".toByteArray()
    Files.write(tmp.resolve("transcript.jsonl"), torn.copyOf(torn.size - 1), StandardOpenOption.APPEND)
    transcribed(tmp)
    expect(chunks(tmp).map { it.to }).toEqual(listOf(5.0, 13.0))
  }

  @Test
  fun `resumes after a chunk whose text holds a line separator`(
    @TempDir tmp: Path,
  ) {
    writeRecording(tmp, listOf(Written("raw.mp3", 0.0, noise(13.0), true)), RecordingState.CAPTURED)
    append(tmp.resolve("transcript.jsonl"), Chunk("raw.mp3", 0.0, 5.0, false, listOf(Segment(0.0, 1.0, "üks\u2028kaks", emptyList()))))
    val stub = StubEars(60.0)
    transcribed(tmp, stub)
    expect(stub.heard).toEqual(listOf(8.0))
  }

  @Test
  fun `opens no ear for a part without audio`(
    @TempDir tmp: Path,
  ) {
    writeRecording(tmp, listOf(Written("raw.mp3", 0.0, ShortArray(0), true)), RecordingState.PARTIAL)
    val stub = StubEars(60.0)
    transcribed(tmp, stub)
    expect(Pair(stub.heard, transcript(tmp).words)).toEqual(Pair(emptyList(), emptyList()))
  }

  @Test
  fun `transcribes while the recording is still running`(
    @TempDir tmp: Path,
  ) {
    writeRecording(tmp, listOf(Written("raw.mp3", 0.0, noise(13.0), false)), RecordingState.RECORDING)
    val running = thread(isDaemon = true) { transcribed(tmp, StubEars(5.0)) }
    waitFor { Files.exists(tmp.resolve("transcript.jsonl")) && chunks(tmp).isNotEmpty() }
    val first = chunks(tmp).first()
    captured(tmp, 13.0)
    running.join(10_000)
    expect(Triple(first.to, running.isAlive, chunks(tmp).last().to)).toEqual(Triple(5.0, false, 13.0))
  }

  @Test
  fun `fails when the recorder dies without a verdict`(
    @TempDir tmp: Path,
  ) {
    writeRecording(tmp, listOf(Written("raw.mp3", 0.0, noise(10.0), false)), RecordingState.RECORDING)
    expect { transcribe(tmp, { StubEars(60.0) }, tagger, { false }, CountDownLatch(1)) }
      .toThrow<IllegalStateException>()
      .messageToContain("still says recording")
  }

  @Test
  fun `leaves a finished transcript alone`(
    @TempDir tmp: Path,
  ) {
    writeRecording(tmp, listOf(Written("raw.mp3", 0.0, noise(2.0), true)), RecordingState.CAPTURED)
    Files.writeString(tmp.resolve("transcript.json"), "{}")
    expect { transcribe(tmp, { error("the model was loaded") }, tagger, { true }, CountDownLatch(1)) }.notToThrow()
  }

  @Test
  fun `names the model and its revision ahead of the transcript`(
    @TempDir tmp: Path,
  ) {
    writeRecording(tmp, listOf(Written("raw.mp3", 0.0, noise(2.0), true)), RecordingState.CAPTURED)
    transcribed(tmp)
    expect(head(tmp.resolve("transcript.json"))).toEqual(listOf("model" to "stub", "revision" to "stub revision"))
  }

  @Test
  fun `writes the sounds before the transcript`(
    @TempDir tmp: Path,
  ) {
    writeRecording(tmp, listOf(Written("raw.mp3", 0.0, noise(2.0), true)), RecordingState.CAPTURED)
    transcribed(tmp)
    expect(Files.getLastModifiedTime(tmp.resolve("sounds.json")))
      .toBeLessThanOrEqualTo(Files.getLastModifiedTime(tmp.resolve("transcript.json")))
  }

  @Test
  fun `returns quietly when stopped after the recorder ended without a verdict`(
    @TempDir tmp: Path,
  ) {
    writeRecording(tmp, listOf(Written("raw.mp3", 0.0, noise(10.0), false)), RecordingState.RECORDING)
    transcribe(tmp, { StubEars(60.0) }, tagger, { false }, CountDownLatch(0))
    expect(Files.exists(tmp.resolve("transcript.json"))).toEqual(false)
  }

  @Test
  fun `finishes the transcript of an ended recording when stopped`(
    @TempDir tmp: Path,
  ) {
    writeRecording(tmp, listOf(Written("raw.mp3", 0.0, noise(10.0), true)), RecordingState.CAPTURED)
    transcribe(tmp, { StubEars(60.0) }, tagger, { false }, CountDownLatch(0))
    expect(Files.exists(tmp.resolve("transcript.json"))).toEqual(true)
  }

  @Test
  fun `writes no final chunk when stopped before a part is heard to its end`(
    @TempDir tmp: Path,
  ) {
    writeRecording(tmp, listOf(Written("raw.mp3", 0.0, noise(30.0), true)), RecordingState.RECORDING)
    val stop = CountDownLatch(1)
    val stub = StubEars(5.0)
    val stopping =
      object : Ears by stub {
        override fun open(): Ear {
          val ear = stub.open()
          return object : Ear by ear {
            override fun hear(samples: FloatArray): List<Utterance> = ear.hear(samples).also { stop.countDown() }
          }
        }
      }
    transcribe(tmp, { stopping }, tagger, { true }, stop)
    expect(Pair(chunks(tmp).map { it.final }, stub.heard)).toEqual(Pair(listOf(false, false), listOf(10.0)))
  }

  @Test
  fun `resumes a part whose pcm is shorter than its last chunk`(
    @TempDir tmp: Path,
  ) {
    writeRecording(tmp, listOf(Written("raw.mp3", 0.0, noise(4.0), true)), RecordingState.CAPTURED)
    append(tmp.resolve("transcript.jsonl"), Chunk("raw.mp3", 0.0, 6.0, false, emptyList()))
    expect { transcribed(tmp) }.notToThrow()
  }

  @Test
  fun `reads only whole samples from a pcm caught mid-write`(
    @TempDir tmp: Path,
  ) {
    val bytes =
      ByteBuffer
        .allocate(32000)
        .order(ByteOrder.LITTLE_ENDIAN)
        .apply { repeat(16000) { putShort(1000) } }
        .array()
    Files.write(tmp.resolve("raw.pcm"), bytes.copyOfRange(0, 16001))
    write(
      tmp.resolve("status.json"),
      Status(RecordingState.RECORDING, "http://127.0.0.1:9/stream.m3u8", 0.0, 0.0, 0.0, listOf(listing("raw.mp3", 0.0, 0.0, false))),
    )
    val stub = StubEars(60.0)
    val running = thread(isDaemon = true) { transcribed(tmp, stub) }
    waitFor { stub.heard.firstOrNull() == 0.5 }
    Files.write(tmp.resolve("raw.pcm"), bytes.copyOfRange(16001, 32000), StandardOpenOption.APPEND)
    captured(tmp, 1.0)
    running.join(10_000)
    expect(Pair(stub.heard, stub.values)).toEqual(Pair(listOf(1.0), setOf(1000 / 32768f)))
  }
}
