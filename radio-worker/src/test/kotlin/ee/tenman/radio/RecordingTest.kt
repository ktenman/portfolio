package ee.tenman.radio

import ch.tutteli.atrium.api.fluent.en_GB.notToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RecordingTest {
  @Test
  fun `leaves out the programmes, slot and lost seconds a status does not have`() {
    val status = Status(RecordingState.RECORDING, KUKU, 1792832100.0, 1792836300.0, 4200.0)
    expect(mapper.writeValueAsString(status)).notToContain("programmes", "slot", "lostSeconds")
  }

  @Test
  fun `reads a status the Python worker wrote`(
    @TempDir tmp: Path,
  ) {
    val file = tmp.resolve("status.json")
    val zeros = "0".repeat(64)
    val json =
      """{"state": "captured", "source": "$KUKU", "startedAt": 1792911570.036, "endsAt": 1792915230.022, "seconds": 3659.9860467910767, "parts": [{"file": "raw.mp3", "streamStart": 29270.903289, "offsetSeconds": 0.0, "durationSeconds": 3659.952, "sha256": "47e250029f84ce3e213090d2ad015bc827e038c426a6aea8d89cf9261f68c4a5", "exitCode": 0}, {"file": "raw-part-002.mp3", "streamStart": 29300.0, "offsetSeconds": 29.097, "durationSeconds": 1.0, "sha256": "$zeros", "exitCode": null}], "programmes": ["Maa elu"], "slot": {"start": 1792911600, "end": 1792915200}, "lostSeconds": 0}"""
    Files.writeString(file, json)
    expect(read<Status>(file)).toEqual(
      Status(
        RecordingState.CAPTURED,
        KUKU,
        1792911570.036,
        1792915230.022,
        3659.9860467910767,
        listOf(
          Part("raw.mp3", 29270.903289, 0.0, 3659.952, "47e250029f84ce3e213090d2ad015bc827e038c426a6aea8d89cf9261f68c4a5", 0),
          Part("raw-part-002.mp3", 29300.0, 29.097, 1.0, zeros, null),
        ),
        listOf("Maa elu"),
        Slot(1792911600, 1792915200),
        0,
      ),
    )
  }

  @Test
  fun `keeps every field of a status through a write and a read`(
    @TempDir tmp: Path,
  ) {
    val status =
      Status(
        RecordingState.PARTIAL,
        KUKU,
        1792832100.0,
        1792836300.0,
        4200.0,
        listOf(Part("raw.mp3", 1.4, 0.0, 4100.5, "a".repeat(64), 0)),
        listOf("Keskpäevatund", "Tarkade klubi"),
        Slot(1792832400, 1792836000),
        99,
      )
    write(tmp.resolve("status.json"), status)
    expect(read<Status>(tmp.resolve("status.json"))).toEqual(status)
  }

  @Test
  fun `rounds half to even on the exact value of the double`() {
    expect(listOf(0.0625, 0.1875, 0.4875).map(::round3)).toEqual(listOf(0.062, 0.188, 0.487))
  }

  @Test
  fun `measures the audio a part repeats from the parts before it`(
    @TempDir tmp: Path,
  ) {
    Files.write(tmp.resolve("raw.pcm"), ByteArray(2 * 10 * RATE))
    Files.write(tmp.resolve("raw-part-002.pcm"), ByteArray(2 * 10 * RATE))
    val parts = listOf(Part("raw.mp3", 1.4, 0.0), Part("raw-part-002.mp3", 5.4, 4.0))
    expect(listOf(overlap(tmp, parts, 0), overlap(tmp, parts, 1))).toEqual(listOf(0.0, 6.0))
  }
}
