package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.FileSystemResource
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

class RadioShowServiceTest {
  @TempDir
  lateinit var directory: Path

  private fun show(parts: List<RecordingPart>): RadioShow =
    RadioShow(
      "Eesti TOP 7 2026-11-14.mp3",
      parts.zip(inpoints(parts)) { part, at -> ShowPart(FileSystemResource(directory.resolve(part.file)), at) },
    )

  @Test
  fun `should start each part where the part before it ended`() {
    val parts =
      listOf(
        RecordingPart("raw-part-000.mp3", 0.0, durationSeconds = 60.0),
        RecordingPart("raw-part-001.mp3", 55.0, durationSeconds = 30.0),
        RecordingPart("raw-part-002.mp3", 100.0, durationSeconds = 10.0),
      )
    expect(inpoints(parts)).toEqual(listOf(0.0, 5.0, 0.0))
  }

  @Test
  fun `should hand over a show of one part as it was recorded`() {
    Files.writeString(directory.resolve("raw.mp3"), "Eesti TOP 7 õhtu")
    val output = ByteArrayOutputStream()
    RadioShowService().write(show(listOf(RecordingPart("raw.mp3", 0.0))), output)
    expect(output.toString(Charsets.UTF_8)).toEqual("Eesti TOP 7 õhtu")
  }

  @Test
  fun `should join two overlapping parts into one show as long as the parts placed by their offsets`() {
    encoded(directory, "raw-part-000", noise(10.0))
    encoded(directory, "raw-part-001", noise(10.0, seed = 9))
    val parts =
      listOf(RecordingPart("raw-part-000.mp3", 0.0, durationSeconds = 10.0), RecordingPart("raw-part-001.mp3", 8.0, durationSeconds = 10.0))
    val joined = directory.resolve("show.mp3")
    Files.newOutputStream(joined).use { RadioShowService().write(show(parts), it) }
    expect(probe(joined)["format"]["duration"].asString().toDouble()).toBeGreaterThan(17.9).toBeLessThan(18.2)
  }
}
