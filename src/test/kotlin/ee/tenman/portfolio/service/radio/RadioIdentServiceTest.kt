package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Station
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

class RadioIdentServiceTest {
  @TempDir
  lateinit var directory: Path

  private val properties by lazy { RadioProperties(recordings = directory) }

  private val service by lazy { service(RadioRenderService()) }

  @Test
  fun `should save the jingle that ends a break under its station and programme`() {
    service.save(KUKU, tape(), listOf(ended()), spoken(16.0))
    expect(idents(properties.idents)).toEqual(listOf(Ident(Station.KUKU, SHOW, "kuku-keskpaevatund.m4a", 5.8, SAVED)))
  }

  @Test
  fun `should cut the saved jingle out of the recording`() {
    service.save(KUKU, tape(), listOf(ended()), spoken(16.0))
    val seconds = probe(properties.idents.resolve("kuku-keskpaevatund.m4a"))["format"]["duration"].asString().toDouble()
    expect(abs(seconds - 5.8) <= 0.05).toEqual(true)
  }

  @Test
  fun `should name the file after the station and the programme without its accents`() {
    val status = RecordingStatus(RecordingState.CAPTURED, station = Station.ARIPAEV)
    service.save(status, tape(), listOf(ended().copy(programme = "Äripäeva õhtu")), spoken(16.0))
    expect(idents(properties.idents).map { it.file }).toEqual(listOf("aripaev-aripaeva-ohtu.m4a"))
  }

  @Test
  fun `should replace the jingle of a programme when it is cut again`() {
    val tape = tape()
    service.save(KUKU, tape, listOf(ended()), spoken(16.0))
    service.save(KUKU, tape, listOf(ended()), spoken(15.0))
    expect(idents(properties.idents).map { it.seconds }).toEqual(listOf(4.8))
  }

  @Test
  fun `should keep the jingles of other programmes`() {
    val other = Ident(Station.ELMAR, "Hommikuprogramm", "elmar-hommikuprogramm.m4a", 6.1, "2026-11-13T05:00:00Z")
    Files.write(Files.createDirectories(properties.idents).resolve(other.file), byteArrayOf(1))
    save(properties.idents.resolve(IDENTS_FILE), listOf(other))
    service.save(KUKU, tape(), listOf(ended()), spoken(16.0))
    expect(idents(properties.idents).map { it.file }).toEqual(listOf(other.file, "kuku-keskpaevatund.m4a"))
  }

  @Test
  fun `should let the episode be cut when the jingle cannot be saved`() {
    val broken = mockk<RadioRenderService>()
    every { broken.ident(any(), any(), any(), any()) } throws IllegalStateException("FFmpeg exited with 1")
    service(broken).save(KUKU, tape(), listOf(ended()), spoken(16.0))
    expect(idents(properties.idents)).toEqual(emptyList())
  }

  private fun service(renderer: RadioRenderService) =
    RadioIdentService(renderer, properties, Clock.fixed(Instant.parse(SAVED), ZoneOffset.UTC))

  private fun tape(): Timeline {
    val recording = Files.createDirectories(directory.resolve("2026-11-14-1200-keskpaevatund"))
    writeRecording(recording, listOf(FixturePart("raw.mp3", 0.0, taped(recording, "raw", 30, "0.3*sin(2*PI*440*t)"))))
    return timeline(recording)
  }

  private fun ended() = Boundary("break 1 ends", 10.0, "station jingle, r=0.9", false, JingleMatch(10.0, 0.9), SHOW)

  private fun spoken(start: Double) = listOf(TranscriptWord(start, start + 0.4, "õhtust"))

  companion object {
    private const val SHOW = "Keskpäevatund"
    private const val SAVED = "2026-11-16T10:00:00Z"
    private val KUKU = RecordingStatus(RecordingState.CAPTURED)
  }
}
