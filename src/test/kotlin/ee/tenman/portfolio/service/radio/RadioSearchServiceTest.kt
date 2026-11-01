package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Station
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

class RadioSearchServiceTest {
  @TempDir
  lateinit var recordings: Path

  private val service by lazy { RadioSearchService(RadioProperties(recordings = recordings)) }

  private fun recording(
    name: String,
    vararg said: Pair<Double, String>,
  ): Path = aired(name, Station.KUKU, *said)

  private fun aired(
    name: String,
    station: Station,
    vararg said: Pair<Double, String>,
  ): Path {
    val directory = Files.createDirectories(recordings.resolve(name))
    save(directory.resolve(STATUS_FILE), RecordingStatus(RecordingState.CAPTURED, 1792230900.0, 4200.0, station = station))
    return transcribed(directory, *said)
  }

  private fun transcribed(
    directory: Path,
    vararg said: Pair<Double, String>,
  ): Path {
    val segments = said.map { (start, text) -> TranscriptSegment(start, start + 2.0, text) }
    save(directory.resolve(TRANSCRIPT_FILE), Transcript(segments, emptyList()))
    return directory
  }

  private fun edited(
    directory: Path,
    keep: List<KeptSpan>,
  ) = save(directory.resolve(EDIT_LIST_FILE), editList(keep))

  private fun later(path: Path) = Files.setLastModifiedTime(path, FileTime.fromMillis(Files.getLastModifiedTime(path).toMillis() + 10_000))

  private fun said(query: String): List<String> =
    service.search(query, null, null).groups.flatMap { group ->
      group.lines.map { line -> "${group.name} ${line.text.joinToString("") { it.text }}" }
    }

  @Test
  fun `should find a line said in a recording`() {
    recording("2026-10-17-1200-keskpaevatund", 12.0 to "Valimised tulevad kevadel")
    expect(said("valimised")).toEqual(listOf("2026-10-17-1200-keskpaevatund Valimised tulevad kevadel"))
  }

  @Test
  fun `should find only the lines of the station asked for`() {
    recording("2026-10-17-1200-keskpaevatund", 12.0 to "Valimised tulevad")
    aired("aripaev-2026-10-17-1400-kuum-tool", Station.ARIPAEV, 12.0 to "Valimised lähenevad")
    expect(service.search("valimised", null, Station.ARIPAEV).groups.map { it.name }).toEqual(listOf("aripaev-2026-10-17-1400-kuum-tool"))
  }

  @Test
  fun `should find the lines of every station when none is asked for`() {
    recording("2026-10-17-1200-keskpaevatund", 12.0 to "Valimised tulevad")
    aired("aripaev-2026-10-17-1400-kuum-tool", Station.ARIPAEV, 12.0 to "Valimised lähenevad")
    expect(service.search("valimised", null, null).recordings).toEqual(2)
  }

  @Test
  fun `should find the new lines of a transcript that was written again`() {
    val directory = recording("2026-10-17-1200-keskpaevatund", 12.0 to "Valimised tulevad")
    said("valimised")
    later(transcribed(directory, 12.0 to "Valimised tulevad", 40.0 to "Eelarve on paigas").resolve(TRANSCRIPT_FILE))
    expect(said("eelarve")).toEqual(listOf("2026-10-17-1200-keskpaevatund Eelarve on paigas"))
  }

  private fun kept(query: String): Boolean =
    service
      .search(query, null, null)
      .groups
      .single()
      .lines
      .single()
      .kept

  @Test
  fun `should mark a line cut once the edit list cuts it`() {
    val directory = recording("2026-10-17-1200-keskpaevatund", 150.0 to "Valimised reklaamis")
    said("valimised")
    edited(directory, listOf(KeptSpan(0.0, 100.0)))
    expect(kept("valimised")).toEqual(false)
  }

  @Test
  fun `should mark a line cut while the review is pending`() {
    val directory = recording("2026-10-17-1200-keskpaevatund", 150.0 to "Valimised reklaamis")
    edited(directory, listOf(KeptSpan(0.0, 4200.0)))
    save(directory.resolve(REVIEW_FILE), listOf(KeptSpan(0.0, 100.0)))
    expect(kept("valimised")).toEqual(false)
  }

  @Test
  @OptIn(ExperimentalPathApi::class)
  fun `should forget a recording whose directory is gone`() {
    val directory = recording("2026-10-17-1200-keskpaevatund", 12.0 to "Valimised tulevad")
    said("valimised")
    directory.deleteRecursively()
    expect(said("valimised")).toEqual(emptyList())
  }

  @Test
  fun `should search the other recordings when one transcript is broken`() {
    recording("2026-10-17-1200-keskpaevatund", 12.0 to "Valimised tulevad")
    Files.writeString(Files.createDirectories(recordings.resolve("2026-10-24-1200-keskpaevatund")).resolve(STATUS_FILE), "{")
    Files.writeString(recordings.resolve("2026-10-24-1200-keskpaevatund").resolve(TRANSCRIPT_FILE), "{")
    expect(said("valimised")).toEqual(listOf("2026-10-17-1200-keskpaevatund Valimised tulevad"))
  }

  @Test
  fun `should not read a broken recording again while its files are unchanged`() {
    val directory = recording("2026-10-24-1200-keskpaevatund")
    Files.writeString(directory.resolve(TRANSCRIPT_FILE), "{")
    said("valimised")
    val written = Files.getLastModifiedTime(directory.resolve(TRANSCRIPT_FILE))
    Files.setLastModifiedTime(transcribed(directory, 12.0 to "Valimised tulevad").resolve(TRANSCRIPT_FILE), written)
    expect(said("valimised")).toEqual(emptyList())
  }

  @Test
  fun `should find a recording once its unchanged transcript can be read again`() {
    val transcript = recording("2026-10-17-1200-keskpaevatund", 12.0 to "Valimised tulevad").resolve(TRANSCRIPT_FILE)
    Files.setPosixFilePermissions(transcript, PosixFilePermissions.fromString("-w-------"))
    said("valimised")
    Files.setPosixFilePermissions(transcript, PosixFilePermissions.fromString("rw-------"))
    expect(said("valimised")).toEqual(listOf("2026-10-17-1200-keskpaevatund Valimised tulevad"))
  }

  @Test
  fun `should index the recordings before the first search`() {
    val directory = recording("2026-10-17-1200-keskpaevatund", 12.0 to "Valimised tulevad")
    val written = Files.getLastModifiedTime(directory.resolve(TRANSCRIPT_FILE))
    service.warm()
    Files.writeString(directory.resolve(TRANSCRIPT_FILE), "{")
    Files.setLastModifiedTime(directory.resolve(TRANSCRIPT_FILE), written)
    expect(said("valimised")).toEqual(listOf("2026-10-17-1200-keskpaevatund Valimised tulevad"))
  }

  @Test
  fun `should reuse a recording while its files are unchanged`() {
    val directory = recording("2026-10-17-1200-keskpaevatund", 12.0 to "Valimised tulevad")
    said("valimised")
    val written = Files.getLastModifiedTime(directory.resolve(TRANSCRIPT_FILE))
    Files.setLastModifiedTime(transcribed(directory, 12.0 to "Eelarve on paigas").resolve(TRANSCRIPT_FILE), written)
    expect(said("valimised")).toEqual(listOf("2026-10-17-1200-keskpaevatund Valimised tulevad"))
  }
}
