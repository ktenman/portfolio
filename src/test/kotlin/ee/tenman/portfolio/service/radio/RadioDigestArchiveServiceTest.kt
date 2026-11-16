package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDate

class RadioDigestArchiveServiceTest {
  @TempDir
  lateinit var recordings: Path

  @Test
  fun `should serve the newest digest first`() {
    writtenDigest(recordings, SUNDAY, listOf("Valimised"))
    writtenDigest(recordings, MONDAY, listOf("Ust-Luga"))
    expect(service().digests().map { it.recording.name }).toEqual(listOf("week-in-radio-2026-11-16", "week-in-radio-2026-11-15"))
  }

  @Test
  fun `should skip a day without stories`() {
    writtenDigest(recordings, SUNDAY, listOf("Valimised"))
    writtenDigest(recordings, MONDAY, emptyList())
    expect(service().digests().map { it.recording.name }).toEqual(listOf("week-in-radio-2026-11-15"))
  }

  @Test
  fun `should find a digest by name with the episode as its only audio`() {
    writtenDigest(recordings, MONDAY, listOf("Ust-Luga", "Telia koondamised"))
    val found = service().recording("week-in-radio-2026-11-16")
    expect(Triple(found?.sizeBytes, found?.audible, found?.timeline?.map { it.title }))
      .toEqual(Triple(0L, listOf(KeptSpan(0.0, 360.0)), listOf("Ust-Luga", "Telia koondamised")))
  }

  @Test
  fun `should serve a digest transcript by name`() {
    writtenDigest(recordings, MONDAY, listOf("Ust-Luga", "Telia koondamised"))
    expect(service().sections("week-in-radio-2026-11-16")?.map { it.start to it.source?.programme })
      .toEqual(listOf(0.0 to "Keskpäevatund", 180.0 to "Keskpäevatund"))
  }

  @Test
  fun `should not serve files other than the episode`() {
    writtenDigest(recordings, MONDAY, listOf("Ust-Luga"))
    expect(service().file("week-in-radio-2026-11-16", "raw.mp3")).toEqual(null)
  }

  @Test
  fun `should not find a digest by a name that only looks like one`() {
    writtenDigest(recordings, MONDAY, listOf("Ust-Luga"))
    expect(service().recording("week-in-radio-../2026-11-16")).toEqual(null)
  }

  private fun service() = RadioDigestArchiveService(RadioProperties(recordings = recordings))

  companion object {
    private val SUNDAY = LocalDate.of(2026, 11, 15)
    private val MONDAY = LocalDate.of(2026, 11, 16)
  }
}
