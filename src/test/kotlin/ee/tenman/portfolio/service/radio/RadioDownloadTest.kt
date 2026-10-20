package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.time.ZoneId

class RadioDownloadTest {
  private val tallinn = ZoneId.of("Europe/Tallinn")

  private fun status(
    programmes: List<String> = listOf("Keskpäevatund"),
    parts: List<String> = listOf("raw.mp3"),
  ) = RecordingStatus(
    RecordingState.CAPTURED,
    1792230900.0,
    4200.0,
    parts.mapIndexed { index, file -> RecordingPart(file, index * 1000.0) },
    programmes,
  )

  @Test
  fun `should name the cut version after the programme and its day`() {
    expect(downloadName("2026-10-17", status(), "episode.m4a", tallinn)).toEqual("Keskpäevatund 2026-10-17.m4a")
  }

  @Test
  fun `should name a single original as the original`() {
    expect(downloadName("2026-10-17", status(), "raw.mp3", tallinn)).toEqual("Keskpäevatund 2026-10-17 originaal.mp3")
  }

  @Test
  fun `should number a part of an original in several parts`() {
    val split = status(parts = listOf("raw.mp3", "raw-part-002.mp3", "raw-part-003.mp3"))
    expect(downloadName("2026-10-17", split, "raw-part-002.mp3", tallinn)).toEqual("Keskpäevatund 2026-10-17 originaal osa 2.mp3")
  }

  @Test
  fun `should name a joined recording after every programme`() {
    val joined = status(programmes = listOf("Keskpäevatund", "Tarkade klubi"))
    expect(downloadName("2026-10-17", joined, "episode.m4a", tallinn)).toEqual("Keskpäevatund, Tarkade klubi 2026-10-17.m4a")
  }

  @Test
  fun `should leave out the characters a file system refuses`() {
    val odd = status(programmes = listOf("Mis toimub? Uudised: ilm/liiklus"))
    expect(downloadName("2026-10-17", odd, "episode.m4a", tallinn)).toEqual("Mis toimub Uudised ilmliiklus 2026-10-17.m4a")
  }

  @Test
  fun `should date a recording by its day in the zone`() {
    val late = RecordingStatus(RecordingState.CAPTURED, 1792270800.0, 3600.0, listOf(RecordingPart("raw.mp3", 0.0)), listOf("Öötund"))
    expect(downloadName("öötund", late, "episode.m4a", tallinn)).toEqual("Öötund 2026-10-18.m4a")
  }

  @Test
  fun `should date a recording by the day its first programme airs`() {
    val early =
      RecordingStatus(RecordingState.CAPTURED, 1792270500.0, 3900.0, listOf(RecordingPart("raw.mp3", 0.0)), listOf("Naistejutud Kukus"))
    expect(downloadName("2026-10-18-0000-naistejutud-kukus", early, "episode.m4a", tallinn)).toEqual("Naistejutud Kukus 2026-10-18.m4a")
  }
}
