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
import kotlin.math.abs

class RadioIdentServiceTest {
  @TempDir
  lateinit var directory: Path

  private val properties by lazy { RadioProperties(recordings = directory) }

  private val service by lazy { service(RadioRenderService()) }

  @Test
  fun `should save the jingle that ends a break under its station and programme`() {
    service.save(cut())
    expect(idents(properties.idents)).toEqual(listOf(Ident(Station.KUKU, SHOW, "kuku-keskpaevatund.m4a", 5.8)))
  }

  @Test
  fun `should cut the saved jingle out of the recording`() {
    service.save(cut())
    val seconds = probe(properties.idents.resolve("kuku-keskpaevatund.m4a"))["format"]["duration"].asString().toDouble()
    expect(abs(seconds - 5.8) <= 0.05).toEqual(true)
  }

  @Test
  fun `should name the file after the station and the programme without its accents`() {
    val recording = cut(ended().copy(programme = "Äripäeva õhtu"))
    save(recording.resolve(STATUS_FILE), readStatus(recording).copy(station = Station.ARIPAEV))
    service.save(recording)
    expect(idents(properties.idents).map { it.file }).toEqual(listOf("aripaev-aripaeva-ohtu.m4a"))
  }

  @Test
  fun `should replace the jingle of a programme when it is cut again`() {
    val recording = cut()
    service.save(recording)
    save(recording.resolve(TRANSCRIPT_FILE), spoken(15.0))
    service.save(recording)
    expect(idents(properties.idents).map { it.seconds }).toEqual(listOf(4.8))
  }

  @Test
  fun `should keep the jingles of other programmes`() {
    val other = Ident(Station.ELMAR, "Hommikuprogramm", "elmar-hommikuprogramm.m4a", 6.1)
    Files.write(Files.createDirectories(properties.idents).resolve(other.file), byteArrayOf(1))
    save(properties.idents.resolve(IDENTS_FILE), listOf(other))
    service.save(cut())
    expect(idents(properties.idents).map { it.file }).toEqual(listOf(other.file, "kuku-keskpaevatund.m4a"))
  }

  @Test
  fun `should save nothing when the jingle cannot be cut`() {
    val broken = mockk<RadioRenderService>()
    every { broken.ident(any(), any(), any(), any()) } throws IllegalStateException("FFmpeg exited with 1")
    service(broken).save(cut())
    expect(idents(properties.idents)).toEqual(emptyList())
  }

  @Test
  fun `should save nothing for a folder that holds no recording`() {
    service.save(Files.createDirectories(directory.resolve("päev")))
    expect(Files.exists(properties.idents)).toEqual(false)
  }

  @Test
  fun `should leave the saved jingles alone when no break ended at a jingle`() {
    service.save(cut(ended().copy(jingle = null)))
    expect(Files.exists(properties.idents)).toEqual(false)
  }

  private fun service(renderer: RadioRenderService) = RadioIdentService(renderer, properties)

  private fun cut(boundary: Boundary = ended()): Path {
    val recording = Files.createDirectories(directory.resolve("2026-11-14-1200-keskpaevatund"))
    writeRecording(recording, listOf(FixturePart("raw.mp3", 0.0, taped(recording, "raw", 30, "0.3*sin(2*PI*440*t)"))))
    save(recording.resolve(EDIT_LIST_FILE), editList(boundaries = listOf(boundary)))
    save(recording.resolve(TRANSCRIPT_FILE), spoken(16.0))
    return recording
  }

  private fun spoken(start: Double) = Transcript(emptyList(), listOf(TranscriptWord(start, start + 0.4, "õhtust")))

  private fun ended() = Boundary("break 1 ends", 10.0, "station jingle, r=0.9", false, JingleMatch(10.0, 0.9), SHOW)

  companion object {
    private const val SHOW = "Keskpäevatund"
  }
}
